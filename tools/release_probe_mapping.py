"""Export only the R8 symbols used by ReleaseRetrofitProbeTest."""
import re
import sys
from pathlib import Path

PACKAGES = ("com.ku.lostandfound.network.", "okhttp3.", "okio.", "retrofit2.", "kotlin.")
CLASSES = {
    "okhttp3.Interceptor", "okhttp3.OkHttpClient", "okhttp3.Protocol", "okhttp3.Response",
    "okhttp3.Headers", "okhttp3.MultipartBody$Part", "okhttp3.RequestBody",
    "okhttp3.RequestBody$Companion$toRequestBody$2", "okio.BufferedSink",
    "okhttp3.Response$Builder", "okhttp3.internal.http.RealInterceptorChain",
    "okhttp3.internal.http.RealResponseBody", "okio.Buffer", "retrofit2.Response",
    "kotlin.coroutines.Continuation", "kotlin.coroutines.EmptyCoroutineContext",
    "kotlin.Result$Failure", "com.google.gson.JsonIOException",
}

symbols = {}
owner = None
with Path(sys.argv[1]).open(encoding="utf-8") as mapping:
    for line in mapping:
        if not line.startswith((" ", "#")) and " -> " in line:
            original, residual = line.rstrip().removesuffix(":").split(" -> ")
            owner = original if (original in CLASSES or original.startswith(PACKAGES[0])) else None
            if owner and not residual.startswith("R8$$REMOVED"):
                symbols[owner] = residual
            else:
                owner = None
        elif owner and line.startswith("    ") and " -> " in line and not line.lstrip().startswith("#"):
            original, residual = line.strip().split(" -> ")
            declaration = re.sub(r"^\d+:\d+:", "", original).split(" ", 1)[-1]
            member = declaration.split("(")[0]
            if "." not in member:
                kind = "()" if "(" in declaration else ""
                symbols.setdefault(f"{owner}#{member}{kind}", residual)
Path(sys.argv[2]).write_text("".join(f"{key.replace(chr(35), '@')}={value}\n" for key, value in symbols.items()), encoding="ascii")
