-- plugin.emailpopup — non-Android passthrough.
-- Translates the plugin's cross-platform options schema to what
-- native.showPopup("mail") expects on iOS/tvOS:
--   attachment.type  → attachment.mimeType
--   to (string)      → to (table)

local M = {}

function M.show(options)
    if not options then
        native.showPopup("mail", {})
        return
    end

    local iosOptions = {}
    for k, v in pairs(options) do
        iosOptions[k] = v
    end

    -- iOS requires an array for recipients
    if type(iosOptions.to) == "string" then
        iosOptions.to = { iosOptions.to }
    end

    -- iOS uses mimeType, not type
    if type(iosOptions.attachment) == "table" and iosOptions.attachment.type and not iosOptions.attachment.mimeType then
        local att = {}
        for k, v in pairs(iosOptions.attachment) do att[k] = v end
        att.mimeType = att.type
        att.type = nil
        iosOptions.attachment = att
    end

    native.showPopup("mail", iosOptions)
end

return M
