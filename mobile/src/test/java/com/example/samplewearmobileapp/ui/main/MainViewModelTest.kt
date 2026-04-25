package com.example.samplewearmobileapp.ui.main

import com.example.samplewearmobileapp.data.repository.FileRepository
import com.example.samplewearmobileapp.data.repository.PolarRepository
import com.example.samplewearmobileapp.data.repository.WearableRepository
import com.example.samplewearmobileapp.domain.model.DeviceConnectionState
import com.example.samplewearmobileapp.domain.model.RecordingState
import com.example.samplewearmobileapp.domain.usecase.ElapsedTimeFormatter
import com.example.samplewearmobileapp.models.Message
import com.example.samplewearmobileapp.models.PpgData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    // -----------------------------------------------------------------------
    // Test Fakes
    // -----------------------------------------------------------------------

    /** Fake PolarRepository that exposes mutable backing state for testing. */
    private class FakePolarRepository : PolarRepository {
        private val _connectionState = MutableStateFlow<DeviceConnectionState>(DeviceConnectionState.Disconnected)
        override val connectionState: StateFlow<DeviceConnectionState> = _connectionState.asStateFlow()

        private val _ecgSamples = MutableSharedFlow<List<Double>>()
        override val ecgSamples = _ecgSamples.asSharedFlow()

        private val _heartRate = MutableSharedFlow<Int>()
        override val heartRate = _heartRate.asSharedFlow()

        var connectCalledWith: String? = null
        var disconnectCalled = false
        var startEcgStreamCalled = false
        var stopEcgStreamCalled = false
        var destroyCalled = false

        override fun connect(deviceId: String) { connectCalledWith = deviceId }
        override fun disconnect() { disconnectCalled = true }
        override suspend fun startEcgStream() { startEcgStreamCalled = true }
        override suspend fun stopEcgStream() { stopEcgStreamCalled = true }
        override fun destroy() { destroyCalled = true }

        fun setConnectionState(state: DeviceConnectionState) { _connectionState.value = state }
        suspend fun emitEcg(samples: List<Double>) = _ecgSamples.emit(samples)
        suspend fun emitHr(bpm: Int) = _heartRate.emit(bpm)
    }

    /** Fake WearableRepository. */
    private class FakeWearableRepository : WearableRepository {
        private val _nodeIds = MutableStateFlow<List<String>>(emptyList())
        override val connectedNodeIds: StateFlow<List<String>> = _nodeIds.asStateFlow()

        private val _messages = MutableSharedFlow<Message>()
        override val incomingMessages = _messages.asSharedFlow()

        val sentMessages = mutableListOf<Pair<Message, String>>()
        var destroyCalled = false

        override suspend fun sendMessage(message: Message, path: String): Boolean {
            sentMessages.add(Pair(message, path))
            return true
        }
        override suspend fun sendData(data: ByteArray, path: String) {}
        override suspend fun refreshConnectedNodes() {}
        override fun destroy() { destroyCalled = true }

        fun setNodes(nodeIds: List<String>) { _nodeIds.value = nodeIds }
    }

    // -----------------------------------------------------------------------
    // Setup
    // -----------------------------------------------------------------------

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakePolar: FakePolarRepository
    private lateinit var fakeWearable: FakeWearableRepository
    private lateinit var fakeFile: FileRepository
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakePolar = FakePolarRepository()
        fakeWearable = FakeWearableRepository()
        fakeFile = mock(FileRepository::class.java)
        viewModel = MainViewModel(fakePolar, fakeWearable, fakeFile, ElapsedTimeFormatter())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -----------------------------------------------------------------------
    // Recording State Transitions
    // -----------------------------------------------------------------------

    @Test
    fun `initial state is Idle`() {
        assertTrue(viewModel.recordingState.value is RecordingState.Idle)
    }

    @Test
    fun `onStartRecording transitions Idle to Recording`() = runTest {
        viewModel.onStartRecording()
        advanceUntilIdle()
        assertTrue(viewModel.recordingState.value is RecordingState.Recording)
    }

    @Test
    fun `onPauseRecording transitions Recording to Paused`() = runTest {
        viewModel.onStartRecording()
        advanceUntilIdle()
        viewModel.onPauseRecording()
        advanceUntilIdle()
        assertTrue(viewModel.recordingState.value is RecordingState.Paused)
    }

    @Test
    fun `onResumeRecording transitions Paused to Recording`() = runTest {
        viewModel.onStartRecording()
        advanceUntilIdle()
        viewModel.onPauseRecording()
        advanceUntilIdle()
        viewModel.onResumeRecording()
        advanceUntilIdle()
        assertTrue(viewModel.recordingState.value is RecordingState.Recording)
    }

    @Test
    fun `onStopRecording returns to Idle from Recording`() = runTest {
        viewModel.onStartRecording()
        advanceUntilIdle()
        viewModel.onStopRecording()
        advanceUntilIdle()
        assertTrue(viewModel.recordingState.value is RecordingState.Idle)
    }

    @Test
    fun `onStopRecording returns to Idle from Paused`() = runTest {
        viewModel.onStartRecording()
        advanceUntilIdle()
        viewModel.onPauseRecording()
        advanceUntilIdle()
        viewModel.onStopRecording()
        advanceUntilIdle()
        assertTrue(viewModel.recordingState.value is RecordingState.Idle)
    }

    @Test
    fun `calling onStartRecording twice does not double-start`() = runTest {
        viewModel.onStartRecording()
        advanceUntilIdle()
        val firstState = viewModel.recordingState.value as RecordingState.Recording
        viewModel.onStartRecording() // should be no-op since not Idle
        advanceUntilIdle()
        val secondState = viewModel.recordingState.value as RecordingState.Recording
        assertEquals(firstState.startEpochMs, secondState.startEpochMs)
    }

    // -----------------------------------------------------------------------
    // Polar connection
    // -----------------------------------------------------------------------

    @Test
    fun `onConnectPolar calls repository connect with correct id`() = runTest {
        viewModel.onConnectPolar("A0B1C2")
        advanceUntilIdle()
        assertEquals("A0B1C2", fakePolar.connectCalledWith)
    }

    @Test
    fun `onConnectPolar with blank id emits ShowToast`() = runTest {
        val events = mutableListOf<UiEvent>()
        val job = backgroundScope.launch {
            viewModel.uiEvents.collect { events.add(it) }
        }
        viewModel.onConnectPolar("")
        advanceUntilIdle()
        assertTrue(events.any { it is UiEvent.ShowToast })
        job.cancel()
    }

    @Test
    fun `onDisconnectPolar calls repository disconnect`() = runTest {
        viewModel.onDisconnectPolar()
        advanceUntilIdle()
        assertTrue(fakePolar.disconnectCalled)
    }

    // -----------------------------------------------------------------------
    // Wearable node state
    // -----------------------------------------------------------------------

    @Test
    fun `isWearConnected is false when no nodes`() = runTest {
        advanceUntilIdle()
        assertFalse(viewModel.isWearConnected.value)
    }

    @Test
    fun `isWearConnected is true when nodes present`() = runTest {
        fakeWearable.setNodes(listOf("node1"))
        advanceUntilIdle()
        assertTrue(viewModel.isWearConnected.value)
    }
}
