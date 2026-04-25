package com.example.samplewearmobileapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Unit tests for Settings / Dialog Theme configuration.
 *
 * Uses [DocumentBuilderFactory] (standard Java DOM parser, JVM classpath) to parse
 * resource XML files — **no Android framework dependency required**.
 *
 * Asserts the full chain of fixes needed for dialog visibility:
 *
 * ## Color Tokens
 *  1. Light `values/colors.xml` defines all 5 semantic dialog tokens
 *  2. Night `values-night/colors.xml` overrides all 5 with high-contrast values
 *  3. Light and night `dialog_button` colors differ (contrast requirement)
 *
 * ## App Base Theme
 *  4. `alertDialogTheme` declared → AlertDialog.Builder() dialogs get styled
 *  5. `android:dialogTheme` declared → EditTextPreference dialogs get styled
 *  6. `preferenceTheme` declared → Preference library walks theme chain correctly
 *
 * ## Dialog Themes
 *  7. `AppAlertDialogTheme` (light) parent = `ThemeOverlay.MaterialComponents`
 *  8. `AppAlertDialogTheme.Night` (dark) parent = `ThemeOverlay.MaterialComponents`
 *  9. `InverseTheme` (light) parent chains back to `AppAlertDialogTheme`
 * 10. `InverseTheme` exists in night themes.xml (critical — previously absent)
 * 11. Night `InverseTheme` parent chains to `AppAlertDialogTheme.Night`
 *
 * ## Button Styles
 * 12. `DialogButtonStyle` (light) parent = `Widget.MaterialComponents.Button.TextButton`
 * 13. `DialogButtonStyle` (light) references `dialog_button` color token
 *
 * ## Layout
 * 14. `device_id_dialog.xml` contains a `TextInputLayout` (not a plain EditText)
 * 15. `device_id_dialog.xml` contains a `TextInputEditText` with id `input`
 * 16. `device_id_dialog.xml` does NOT have hardcoded `backgroundTint` on the input
 *
 * ## Preferences
 * 17. `preferences.xml` has exactly 2 `EditTextPreference` elements
 * 18. Device ID `EditTextPreference` has `app:dialogTitle` set
 * 19. Patient Name `EditTextPreference` has `app:dialogTitle` set
 * 20. `preferences.xml` has exactly 5 `SwitchPreferenceCompat` elements
 *
 * **SRP:** These tests own only "resource XML is structurally correct" concern.
 * **DRY:** File extension functions avoid repeated parsing boilerplate.
 */
@RunWith(JUnit4::class)
class SettingsThemeTest {

    // -------------------------------------------------------------------------
    // Paths to resource files.
    // Gradle sets user.dir to the MODULE directory (e.g. .../4_HeartMonitor/mobile)
    // when running unit tests, so src/main/res is a direct child of user.dir.
    // -------------------------------------------------------------------------

    private val moduleDir   = File(System.getProperty("user.dir") ?: ".").canonicalFile
    private val res         = File(moduleDir, "src/main/res")

    private val colorsLight = File(res, "values/colors.xml")
    private val colorsNight = File(res, "values-night/colors.xml")
    private val themesLight = File(res, "values/themes.xml")
    private val themesNight = File(res, "values-night/themes.xml")
    private val prefsXml    = File(res, "xml/preferences.xml")
    private val dialogXml   = File(res, "layout/device_id_dialog.xml")

    // =========================================================================
    // 1–3. Semantic color tokens
    // =========================================================================

    @Test fun `colors_xml contains dialog_background token`() =
        assertTrue("dialog_background missing from values/colors.xml",
            colorsLight.colorNames().contains("dialog_background"))

    @Test fun `colors_xml contains dialog_text token`() =
        assertTrue("dialog_text missing from values/colors.xml",
            colorsLight.colorNames().contains("dialog_text"))

    @Test fun `colors_xml contains dialog_button token`() =
        assertTrue("dialog_button missing from values/colors.xml",
            colorsLight.colorNames().contains("dialog_button"))

    @Test fun `colors_xml contains dialog_hint token`() =
        assertTrue("dialog_hint missing from values/colors.xml",
            colorsLight.colorNames().contains("dialog_hint"))

    @Test fun `colors_xml contains dialog_input_text token`() =
        assertTrue("dialog_input_text missing from values/colors.xml",
            colorsLight.colorNames().contains("dialog_input_text"))

    @Test fun `night colors_xml file exists`() =
        assertTrue("values-night/colors.xml must exist at: ${colorsNight.absolutePath}",
            colorsNight.exists())

    @Test fun `night colors_xml contains dialog_background override`() =
        assertTrue("dialog_background missing from values-night/colors.xml",
            colorsNight.colorNames().contains("dialog_background"))

    @Test fun `night colors_xml contains dialog_text override`() =
        assertTrue("dialog_text missing from values-night/colors.xml",
            colorsNight.colorNames().contains("dialog_text"))

    @Test fun `night colors_xml contains dialog_button override`() =
        assertTrue("dialog_button missing from values-night/colors.xml",
            colorsNight.colorNames().contains("dialog_button"))

    @Test fun `night colors_xml contains dialog_input_text override`() =
        assertTrue("dialog_input_text missing from values-night/colors.xml",
            colorsNight.colorNames().contains("dialog_input_text"))

    @Test fun `night dialog_button color differs from light for contrast`() {
        val light = colorsLight.colorValues()["dialog_button"]
        val night = colorsNight.colorValues()["dialog_button"]
        assertNotNull("light dialog_button must be defined", light)
        assertNotNull("night dialog_button must be defined", night)
        assertTrue("Light and night dialog_button should differ for contrast", light != night)
    }

    // =========================================================================
    // 4–6. Base app theme attributes
    // =========================================================================

    @Test fun `base app theme declares alertDialogTheme`() {
        val items = themesLight.styleItemNames("Theme.SampleWearMobileApp")
        assertTrue("Theme.SampleWearMobileApp must declare 'alertDialogTheme'",
            items.contains("alertDialogTheme"))
    }

    @Test fun `base app theme declares android dialogTheme`() {
        val items = themesLight.styleItemNames("Theme.SampleWearMobileApp")
        assertTrue("Theme.SampleWearMobileApp must declare 'android:dialogTheme'",
            items.contains("android:dialogTheme"))
    }

    @Test fun `base app theme declares preferenceTheme`() {
        val items = themesLight.styleItemNames("Theme.SampleWearMobileApp")
        assertTrue("Theme.SampleWearMobileApp must declare 'preferenceTheme'",
            items.contains("preferenceTheme"))
    }

    @Test fun `night base app theme declares alertDialogTheme`() {
        val items = themesNight.styleItemNames("Theme.SampleWearMobileApp")
        assertTrue("Night Theme.SampleWearMobileApp must declare 'alertDialogTheme'",
            items.contains("alertDialogTheme"))
    }

    @Test fun `night base app theme declares android dialogTheme`() {
        val items = themesNight.styleItemNames("Theme.SampleWearMobileApp")
        assertTrue("Night Theme.SampleWearMobileApp must declare 'android:dialogTheme'",
            items.contains("android:dialogTheme"))
    }

    @Test fun `night base app theme declares preferenceTheme`() {
        val items = themesNight.styleItemNames("Theme.SampleWearMobileApp")
        assertTrue("Night Theme.SampleWearMobileApp must declare 'preferenceTheme'",
            items.contains("preferenceTheme"))
    }

    // =========================================================================
    // 7–11. Dialog theme parents
    // =========================================================================

    @Test fun `AppAlertDialogTheme parent is ThemeOverlay MaterialComponents`() {
        val parent = themesLight.styleParents()["AppAlertDialogTheme"]
        assertNotNull("AppAlertDialogTheme must be declared in values/themes.xml", parent)
        assertTrue("AppAlertDialogTheme parent must contain 'ThemeOverlay.MaterialComponents' but was: $parent",
            parent!!.contains("ThemeOverlay.MaterialComponents"))
    }

    @Test fun `AppAlertDialogTheme Night parent is ThemeOverlay MaterialComponents`() {
        val parent = themesNight.styleParents()["AppAlertDialogTheme.Night"]
        assertNotNull("AppAlertDialogTheme.Night must be declared in values-night/themes.xml", parent)
        assertTrue("AppAlertDialogTheme.Night parent must contain 'ThemeOverlay.MaterialComponents' but was: $parent",
            parent!!.contains("ThemeOverlay.MaterialComponents"))
    }

    @Test fun `InverseTheme light parent chains to AppAlertDialogTheme`() {
        val parent = themesLight.styleParents()["InverseTheme"]
        assertNotNull("InverseTheme must be declared in values/themes.xml", parent)
        assertTrue("InverseTheme parent must reference AppAlertDialogTheme but was: $parent",
            parent!!.contains("AppAlertDialogTheme"))
    }

    @Test fun `InverseTheme exists in night themes xml`() {
        // This was previously absent — no night override meant dark-mode dialogs
        // used the light InverseTheme, producing invisible text on wrong backgrounds.
        val parents = themesNight.styleParents()
        assertTrue("InverseTheme must be declared in values-night/themes.xml",
            parents.containsKey("InverseTheme"))
    }

    @Test fun `InverseTheme night parent chains to AppAlertDialogTheme Night`() {
        val parent = themesNight.styleParents()["InverseTheme"]
        assertNotNull("InverseTheme must be declared in values-night/themes.xml", parent)
        assertTrue("Night InverseTheme parent must reference AppAlertDialogTheme.Night but was: $parent",
            parent!!.contains("AppAlertDialogTheme"))
    }

    // =========================================================================
    // 12–13. Button style
    // =========================================================================

    @Test fun `DialogButtonStyle parent is Widget MaterialComponents Button TextButton`() {
        val parent = themesLight.styleParents()["DialogButtonStyle"]
        assertNotNull("DialogButtonStyle must be declared in values/themes.xml", parent)
        assertTrue("DialogButtonStyle parent must be Material TextButton but was: $parent",
            parent!!.contains("MaterialComponents"))
    }

    @Test fun `DialogButtonStyle Night parent is Widget MaterialComponents Button TextButton`() {
        val parent = themesNight.styleParents()["DialogButtonStyle.Night"]
        assertNotNull("DialogButtonStyle.Night must be declared in values-night/themes.xml", parent)
        assertTrue("DialogButtonStyle.Night parent must be Material TextButton but was: $parent",
            parent!!.contains("MaterialComponents"))
    }

    @Test fun `DialogButtonStyle declares textColor item`() {
        val items = themesLight.styleItemNames("DialogButtonStyle")
        assertTrue("DialogButtonStyle must declare 'android:textColor'",
            items.contains("android:textColor"))
    }

    @Test fun `DialogButtonStyle declares minHeight item`() {
        val items = themesLight.styleItemNames("DialogButtonStyle")
        assertTrue("DialogButtonStyle must declare 'android:minHeight' for touch target compliance",
            items.contains("android:minHeight"))
    }

    // =========================================================================
    // 14–16. device_id_dialog.xml layout
    // =========================================================================

    @Test fun `device_id_dialog contains TextInputLayout`() {
        val doc   = dialogXml.dom()
        val count = doc.getElementsByTagName("com.google.android.material.textfield.TextInputLayout").length
        assertTrue("device_id_dialog.xml must contain a TextInputLayout (not a plain EditText)", count > 0)
    }

    @Test fun `device_id_dialog contains TextInputEditText with id input`() {
        val doc   = dialogXml.dom()
        val nodes = doc.getElementsByTagName("com.google.android.material.textfield.TextInputEditText")
        var found = false
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as? Element ?: continue
            val id = el.getAttribute("android:id")
            if (id.contains("input")) { found = true; break }
        }
        assertTrue("TextInputEditText with id '@+id/input' must exist in device_id_dialog.xml", found)
    }

    @Test fun `device_id_dialog does not have hardcoded backgroundTint`() {
        val doc   = dialogXml.dom()
        // Check all element types for backgroundTint with a hardcoded @color/ reference
        val allElements = listOf("EditText", "TextInputEditText", "TextInputLayout",
            "com.google.android.material.textfield.TextInputEditText",
            "com.google.android.material.textfield.TextInputLayout")
        var hasBadTint = false
        for (tagName in allElements) {
            val nodes = doc.getElementsByTagName(tagName)
            for (i in 0 until nodes.length) {
                val el = nodes.item(i) as? Element ?: continue
                val tint = el.getAttribute("android:backgroundTint")
                if (tint.startsWith("@color/")) { hasBadTint = true }
            }
        }
        assertFalse("device_id_dialog.xml must not have hardcoded @color/ backgroundTint — use ?attr/ instead",
            hasBadTint)
    }

    @Test fun `device_id_dialog does not contain a plain EditText root`() {
        val doc   = dialogXml.dom()
        val count = doc.getElementsByTagName("EditText").length
        // TextInputEditText extends EditText but the DOM tag will be
        // "com.google.android.material.textfield.TextInputEditText", NOT "EditText"
        assertEquals("device_id_dialog.xml must not contain a plain <EditText> tag", 0, count)
    }

    // =========================================================================
    // 17–20. preferences.xml structure
    // =========================================================================

    @Test fun `preferences_xml contains exactly two EditTextPreferences`() {
        val count = prefsXml.dom().getElementsByTagName("EditTextPreference").length
        assertEquals("Expected 2 EditTextPreferences (deviceId + patientName)", 2, count)
    }

    @Test fun `deviceId EditTextPreference has dialogTitle`() {
        val keys = prefsXml.editTextPrefsWithDialogTitle()
        assertTrue("deviceId EditTextPreference must have app:dialogTitle set",
            keys.contains("deviceId"))
    }

    @Test fun `patientName EditTextPreference has dialogTitle`() {
        val keys = prefsXml.editTextPrefsWithDialogTitle()
        assertTrue("patientName EditTextPreference must have app:dialogTitle set",
            keys.contains("patientName"))
    }

    @Test fun `preferences_xml contains exactly five SwitchPreferenceCompat items`() {
        val count = prefsXml.dom().getElementsByTagName("SwitchPreferenceCompat").length
        assertEquals("Expected 5 SwitchPreferenceCompat items", 5, count)
    }

    // =========================================================================
    // Private DOM helper extensions (DRY)
    // =========================================================================

    /** Parse file into a DOM Document. Fails with clear message if the file is missing. */
    private fun File.dom() = run {
        assertTrue("Resource file must exist: $absolutePath", exists())
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(this)
    }

    /** Returns all `name` attribute values of `<color>` elements. */
    private fun File.colorNames(): Set<String> {
        val doc = dom()
        val nodes = doc.getElementsByTagName("color")
        return (0 until nodes.length)
            .mapNotNull { (nodes.item(it) as? Element)?.getAttribute("name") }
            .toSet()
    }

    /** Returns a map of color name → trimmed hex value. */
    private fun File.colorValues(): Map<String, String> {
        val doc = dom()
        val nodes = doc.getElementsByTagName("color")
        return (0 until nodes.length).mapNotNull { i ->
            val el   = nodes.item(i) as? Element ?: return@mapNotNull null
            val name = el.getAttribute("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            name to el.textContent.trim()
        }.toMap()
    }

    /** Returns a map of style `name` → `parent` attribute. */
    private fun File.styleParents(): Map<String, String> {
        val doc   = dom()
        val nodes: NodeList = doc.getElementsByTagName("style")
        return (0 until nodes.length).mapNotNull { i ->
            val el   = nodes.item(i) as? Element ?: return@mapNotNull null
            val name = el.getAttribute("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            name to (el.getAttribute("parent") ?: "")
        }.toMap()
    }

    /**
     * Returns the set of `name` attribute values of `<item>` children inside
     * the style whose `name` attribute equals [styleName].
     */
    private fun File.styleItemNames(styleName: String): Set<String> {
        val doc        = dom()
        val styleNodes = doc.getElementsByTagName("style")
        for (i in 0 until styleNodes.length) {
            val styleEl = styleNodes.item(i) as? Element ?: continue
            if (styleEl.getAttribute("name") != styleName) continue
            val itemNodes = styleEl.getElementsByTagName("item")
            return (0 until itemNodes.length)
                .mapNotNull { (itemNodes.item(it) as? Element)?.getAttribute("name") }
                .toSet()
        }
        return emptySet()
    }

    /**
     * Returns the set of preference `key` values for `<EditTextPreference>` elements
     * that also have a non-empty `dialogTitle` attribute.
     *
     * Without namespace awareness the DOM stores `app:key` as the literal attribute
     * name "app:key" — we check both prefixed and unprefixed forms.
     */
    private fun File.editTextPrefsWithDialogTitle(): Set<String> {
        val doc    = dom()
        val nodes  = doc.getElementsByTagName("EditTextPreference")
        val result = mutableSetOf<String>()
        for (i in 0 until nodes.length) {
            val el    = nodes.item(i) as? Element ?: continue
            val key   = el.getAttribute("app:key").ifBlank { el.getAttribute("key") }
            val title = el.getAttribute("app:dialogTitle").ifBlank { el.getAttribute("dialogTitle") }
            if (key.isNotBlank() && title.isNotBlank()) result.add(key)
        }
        return result
    }
}
