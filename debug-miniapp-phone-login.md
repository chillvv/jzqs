[OPEN] Miniapp phone login debug session

# Session
- session_id: miniapp-phone-login
- created_at: 2026-06-07
- scope: customer miniapp and rider miniapp getPhoneNumber runtime debugging

# Symptom
- WeChat one-click phone authorization still does not return a usable phone login result in the miniapps.

# Hypotheses
- H1: Frontend button callback does not receive a valid `e.detail.code`.
- H2: Frontend still sends the wrong payload or hits an old backend endpoint.
- H3: Backend receives the request but runs in `wechat.dev-mode=true` or other non-production path.
- H4: Backend calls WeChat phone API and gets an `errcode/errmsg` caused by token, appid, secret, or capability mismatch.
- H5: The currently running deployed service is not the code version that contains the new phone-code flow or debug instrumentation.

# Evidence Log
- Existing instrumentation in codebase still reports to session `wechat-phone-login`.
- `.dbg/wechat-phone-login.env` exists and points to `http://192.168.1.3:7777/event`.
- Debug server was not running at the start of this session; health checks to `127.0.0.1:7777` and `192.168.1.3:7777` initially failed.
- Debug server has now been restarted successfully for runtime evidence collection.
- `backend/src/main/resources/application.yml` still defaults `wechat.dev-mode` to `${WECHAT_DEV_MODE:true}`.

# Next Step
- Ask user to reproduce once with the restarted debug server, then inspect collected logs from the active `wechat-phone-login` session.
