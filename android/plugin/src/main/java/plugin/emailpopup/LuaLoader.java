// LuaLoader for plugin.emailpopup (Android variant).
// Entry point: require("plugin.emailpopup")
// Returns a Lua module {show = function(options) ... end}.
// The Lua shim resolves Solar2D baseDir constants via system.pathForFile and
// hands absolute paths to the Java impl. Non-Android platforms ship a separate
// Lua-only variant of this plugin that calls native.showPopup directly.

package plugin.emailpopup;

import com.naef.jnlua.JavaFunction;
import com.naef.jnlua.LuaState;

@SuppressWarnings({"WeakerAccess", "unused"})
public class LuaLoader implements JavaFunction {

    private static final String SHIM_SOURCE =
            "local native_impl = ...\n" +
            "local M = {}\n" +
            "\n" +
            "local function resolveAttachments(attachment)\n" +
            "    if attachment == nil then return nil end\n" +
            "    if attachment.filename then\n" +
            "        return {{\n" +
            "            absolutePath = system.pathForFile(attachment.filename, attachment.baseDir or system.DocumentsDirectory),\n" +
            "            type = attachment.type or '*/*',\n" +
            "        }}\n" +
            "    end\n" +
            "    local out = {}\n" +
            "    for i, a in ipairs(attachment) do\n" +
            "        out[i] = {\n" +
            "            absolutePath = system.pathForFile(a.filename, a.baseDir or system.DocumentsDirectory),\n" +
            "            type = a.type or '*/*',\n" +
            "        }\n" +
            "    end\n" +
            "    return out\n" +
            "end\n" +
            "\n" +
            "function M.show(options)\n" +
            "    options = options or {}\n" +
            "    native_impl({\n" +
            "        to = options.to,\n" +
            "        cc = options.cc,\n" +
            "        bcc = options.bcc,\n" +
            "        subject = options.subject,\n" +
            "        body = options.body,\n" +
            "        isBodyHtml = options.isBodyHtml,\n" +
            "        attachment = resolveAttachments(options.attachment),\n" +
            "    })\n" +
            "end\n" +
            "\n" +
            "return M\n";

    @SuppressWarnings("unused")
    public LuaLoader() {
    }

    @Override
    public int invoke(LuaState L) {
        // Stack: [moduleName]
        // Push native impl as the only function value we'll hand to the shim.
        L.pushJavaFunction(new SendEmailWithAttachment());
        // Stack: [moduleName, native_impl_fn]

        // Load the embedded Lua shim. After load(): a callable chunk is on top.
        L.load(SHIM_SOURCE, "plugin.emailpopup");
        // Stack: [moduleName, native_impl_fn, chunk]

        // Move chunk below native_impl_fn so we can pass native_impl_fn as the chunk's vararg.
        L.insert(-2);
        // Stack: [moduleName, chunk, native_impl_fn]

        // Call chunk with native_impl_fn as ..., expect 1 return (the module table).
        L.call(1, 1);
        // Stack: [moduleName, module]

        return 1;
    }
}
