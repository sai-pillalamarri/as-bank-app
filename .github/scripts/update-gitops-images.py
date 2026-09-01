from pathlib import Path
import os
import re
import sys


SERVICES = {
    "customer-service": "CUSTOMER_DIGEST",
    "account-service": "ACCOUNT_DIGEST",
    "transaction-service": "TRANSACTION_DIGEST",
    "frontend": "FRONTEND_DIGEST",
}


def newline_for(line: str) -> str:
    return "\r\n" if line.endswith("\r\n") else "\n"


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit(
            "usage: update-gitops-images.py <values-dev.yaml>"
        )

    path = Path(sys.argv[1])
    release_sha = os.environ["RELEASE_SHA"]

    if not re.fullmatch(r"[0-9a-f]{40}", release_sha):
        raise SystemExit("RELEASE_SHA must be a 40-character Git SHA")

    updates = {
        service: os.environ.get(variable, "").strip()
        for service, variable in SERVICES.items()
    }

    updates = {
        service: digest
        for service, digest in updates.items()
        if digest
    }

    if not updates:
        raise SystemExit("no image digests were supplied")

    for service, digest in updates.items():
        if not re.fullmatch(r"sha256:[0-9a-f]{64}", digest):
            raise SystemExit(
                f"{service} has an invalid image digest"
            )

    lines = path.read_text(encoding="utf-8").splitlines(
        keepends=True
    )

    current_service = None
    changed = {
        service: {
            "version": False,
            "digest": False,
        }
        for service in updates
    }

    service_pattern = re.compile(
        r"^  ([a-z0-9-]+):\s*$"
    )

    for index, line in enumerate(lines):
        match = service_pattern.match(
            line.rstrip("\r\n")
        )

        if match:
            name = match.group(1)
            current_service = (
                name if name in updates else None
            )
            continue

        if current_service is None:
            continue

        if line.startswith("    version:"):
            ending = newline_for(line)
            lines[index] = (
                f'    version: "{release_sha[:12]}"'
                f"{ending}"
            )
            changed[current_service]["version"] = True
            continue

        if line.startswith("      digest:"):
            ending = newline_for(line)
            lines[index] = (
                f"      digest: "
                f"{updates[current_service]}"
                f"{ending}"
            )
            changed[current_service]["digest"] = True

    missing = [
        f"{service}:{field}"
        for service, fields in changed.items()
        for field, found in fields.items()
        if not found
    ]

    if missing:
        raise SystemExit(
            "expected GitOps fields were not found: "
            + ", ".join(missing)
        )

    path.write_text(
        "".join(lines),
        encoding="utf-8",
        newline="",
    )

    print(
        "Updated: "
        + ", ".join(sorted(updates))
    )


if __name__ == "__main__":
    main()