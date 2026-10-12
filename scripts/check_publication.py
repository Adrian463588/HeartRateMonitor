"""Reject tracked/incoming SDK bundles, signing material and raw recordings before publication."""
import argparse
import subprocess
import sys
from pathlib import PurePosixPath

BLOCKED_SUFFIXES = {
    ".aar", ".apk", ".aab", ".jks", ".keystore", ".key", ".pem", ".p12", ".pfx",
    ".csv", ".tsv", ".parquet", ".feather", ".h5", ".hdf5", ".npy", ".npz",
    ".jsonl", ".edf", ".bdf", ".fif", ".mat", ".pyc", ".class", ".dex", ".so",
}
BLOCKED_NAMES = {"local.properties", "key.properties", "google-services.json", "credentials.json"}
BLOCKED_DIRS = {".idea", ".gradle", ".kotlin", "build", "bin", "__pycache__", "raw_data", "sensor_data", "recordings"}
BLOCKED_LOGS = {"build_output.txt", "compile_output.txt", "compile_output2.txt"}


def forbidden_path(path):
    parsed = PurePosixPath(path.replace("\\", "/").lower())
    return (parsed.suffix in BLOCKED_SUFFIXES or parsed.name in BLOCKED_NAMES
            or parsed.name in BLOCKED_LOGS or parsed.name == ".env"
            or parsed.name.startswith(".env.") or bool(set(parsed.parts[:-1]) & BLOCKED_DIRS))


def git(*args):
    result = subprocess.run(["git", *args], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    return result.stdout.decode("utf-8", errors="strict")


def audit(base=None, head="HEAD"):
    current = {path for path in git("ls-files", "-z").split("\0") if path}
    failures = [f"tracked:{path}" for path in sorted(current) if forbidden_path(path)]
    commits = []
    if base:
        base_sha = git("rev-parse", "--verify", f"{base}^{{commit}}").strip()
        head_sha = git("rev-parse", "--verify", f"{head}^{{commit}}").strip()
        commits = git("rev-list", f"{base_sha}..{head_sha}").splitlines()
        for commit in commits:
            # Inspect each merge parent: a deleted SDK in an incoming commit
            # still enters history when the PR is merged without squash.
            changed = git("diff-tree", "--root", "-r", "-m", "--no-commit-id",
                          "--name-only", "--diff-filter=ACMR", "-z", commit)
            failures.extend(f"incoming:{commit}:{path}" for path in set(changed.split("\0"))
                            if path and forbidden_path(path))
    return sorted(set(failures)), len(current), len(commits)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", help="Existing destination commit/ref; scan all incoming history.")
    parser.add_argument("--head", default="HEAD")
    args = parser.parse_args()
    try:
        failures, file_count, commit_count = audit(args.base, args.head)
    except (subprocess.CalledProcessError, UnicodeError) as error:
        print(f"Publication audit could not finish: {type(error).__name__}", file=sys.stderr)
        return 2
    if failures:
        print("\n".join(failures), file=sys.stderr)
        print(f"Publication audit FAILED: {len(failures)} prohibited paths.", file=sys.stderr)
        return 1
    print(f"Publication audit passed: {file_count} tracked paths, {commit_count} incoming commits.")
    print("SDKs and raw recordings must stay local/ignored. Existing destination history is not rewritten.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
