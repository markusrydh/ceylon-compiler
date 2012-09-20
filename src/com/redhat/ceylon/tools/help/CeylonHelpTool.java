package com.redhat.ceylon.tools.help;


import com.redhat.ceylon.common.tool.Argument;
import com.redhat.ceylon.common.tool.Description;
import com.redhat.ceylon.common.tool.Option;
import com.redhat.ceylon.common.tool.RemainingSections;
import com.redhat.ceylon.common.tool.Summary;
import com.redhat.ceylon.common.tool.Tool;
import com.redhat.ceylon.common.tool.ToolLoader;
import com.redhat.ceylon.common.tool.ToolModel;
import com.redhat.ceylon.common.tool.Tools;
import com.redhat.ceylon.common.tool.WordWrap;
import com.redhat.ceylon.tools.CeylonTool;
import com.redhat.ceylon.tools.help.model.Doc;
import com.redhat.ceylon.tools.help.model.Visitor;

/**
 * A plugin which provides help about other plugins 
 * @author tom
 */
@Summary("Display help information about other ceylon tools")
@Description(
"If a `<tool>` is given, displays help about that ceylon tool on the standard output.\n\n" +
"If no `<tool>` is given, displays the synopsis of the top level `ceylon` command. "
)
@RemainingSections(
"## SEE ALSO\n\n" +
"* `ceylon doc-tool` for generating documentation about ceylon tools\n"
)
public class CeylonHelpTool implements Tool {

    private ToolModel<?> tool;
    private Appendable out = System.out;
    private boolean includeHidden;
    private ToolLoader toolLoader;
    private DocBuilder docBuilder;
    
    public final void setToolLoader(ToolLoader toolLoader) {
        this.toolLoader = toolLoader;
        this.docBuilder = new DocBuilder(toolLoader);
    }
    
    @Option
    public void setIncludeHidden(boolean includeHidden) {
        this.includeHidden = includeHidden;
    }
    
    @Argument(argumentName="tool", multiplicity="?")
    public void setTool(ToolModel<?> tool) {
        this.tool = tool;
    }
    
    public void setOut(Appendable out) {
        this.out = out;
    }
    
    @Override
    public void run() {
        final WordWrap wrap = new WordWrap(out);
        Visitor plain = new PlainVisitor(wrap);
        if (tool == null) {
            printTopLevelHelp(plain, toolLoader.getToolNames());
        } else {
            if (tool != null) {
                docBuilder.buildDoc(tool).accept(plain);
            }/* else {
                Tools.printToolSuggestions(toolLoader, wrap, tool);
            }*/
        }
        wrap.flush();
    }
    
    protected final void printTopLevelHelp(Visitor out, Iterable<String> toolNames) {
        final ToolModel<CeylonTool> root = toolLoader.loadToolModel("");
        final Doc docModel = docBuilder.buildDoc(root);
        docModel.accept(out);
    }

    

}
