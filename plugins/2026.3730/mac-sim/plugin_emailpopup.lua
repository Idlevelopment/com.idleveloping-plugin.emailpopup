-- plugin.emailpopup — non-Android passthrough.
-- Solar2D's built-in native.showPopup("mail", ...) already opens a real mail
-- composer (not a share sheet) on iOS, macOS, and tvOS. This shim exists so
-- caller code can `require("plugin.emailpopup").show(opts)` uniformly across
-- platforms without per-call branching.

local M = {}

function M.show(options)
    native.showPopup("mail", options or {})
end

return M
