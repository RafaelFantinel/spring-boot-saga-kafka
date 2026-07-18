#!/usr/bin/env bash
# Remove the refarch workloads (keeps the minikube cluster running).
# Pass --cluster to also stop/delete the minikube VM.
set -euo pipefail
export PATH="$HOME/.local/bin:$PATH"

kubectl delete namespace refarch --ignore-not-found

if [ "${1:-}" = "--cluster" ]; then
  minikube delete
fi
