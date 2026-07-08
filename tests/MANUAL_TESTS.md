# Manual-only tests

Everything else in `Tests.md` is covered by `tests/test_*.sh` and `run_all.sh`.
These two sections can't be automated from a script running alongside the
server, and are left as a manual checklist instead:

## #34 Configuration Test

Requires editing `config.json` and **restarting** the server process, which a
test script running independently of the server has no safe way to trigger
(it doesn't own the server's lifecycle, and killing/restarting it from
another script risks racing with whoever else is using it).

Steps:
1. Stop the server.
2. Edit `config.json`: change a port, a route's `root`, the upload folder,
   or an error page path.
3. Restart: `./run.sh`
4. Confirm the change took effect (e.g. new port responds, files now serve
   from the new root) **without recompiling any Java code**.

## #38 Browser Tests

Requires a real browser rendering the page, which `curl` can't stand in for
(CSS actually applying, JS actually executing, a file download dialog
appearing, a login/session flow working visually).

Open `http://localhost:8080` (or your configured host/port) in a browser and
manually verify:
- HTML pages render correctly
- CSS loads and applies
- JavaScript loads and runs
- Images display
- Redirects navigate correctly
- File downloads work
- Upload forms work
- Cookies are set (check browser dev tools → Application/Storage)
- Sessions persist across page reloads
