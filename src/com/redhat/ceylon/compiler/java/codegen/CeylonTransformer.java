/*
 * Copyright Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the authors tag. All rights reserved.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU General Public License version 2.
 * 
 * This particular file is subject to the "Classpath" exception as provided in the 
 * LICENSE file that accompanied this code.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License,
 * along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */

package com.redhat.ceylon.compiler.java.codegen;

import static com.sun.tools.javac.code.Flags.FINAL;

import java.util.Iterator;

import javax.tools.JavaFileObject;

import com.redhat.ceylon.compiler.typechecker.context.PhasedUnit;
import com.redhat.ceylon.compiler.typechecker.model.Parameter;
import com.redhat.ceylon.compiler.typechecker.model.Setter;
import com.redhat.ceylon.compiler.typechecker.model.TypedDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.Value;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCImport;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Options;
import com.sun.tools.javac.util.Position.LineMap;

/**
 * Main transformer that delegates all transforming of ceylon to java to auxiliary classes.
 */
public class CeylonTransformer extends AbstractTransformer {
    private Options options;
    private LineMap map;
    private JavaFileObject fileObject;
    public boolean disableModelAnnotations = false;
    CeylonVisitor visitor;
    
    public static CeylonTransformer getInstance(Context context) {
        CeylonTransformer trans = context.get(CeylonTransformer.class);
        if (trans == null) {
            trans = new CeylonTransformer(context);
            context.put(CeylonTransformer.class, trans);
        }
        return trans;
    }

    public CeylonTransformer(Context context) {
        super(context);
        setup(context);
    }

    private void setup(Context context) {
        options = Options.instance(context);
        // It's a bit weird to see "invokedynamic" set here,
        // but it has to be done before Resolve.instance().
        options.put("invokedynamic", "invokedynamic");
    }

    @Override
    public void setMap(LineMap map) {
        this.map = map;
    }

    @Override
    protected LineMap getMap() {
        return map;
    }

    public void setFileObject(JavaFileObject fileObject) {
        this.fileObject = fileObject;
    }
    
    public JavaFileObject getFileObject() {
        return fileObject;
    }

    /**
     * In this pass we only make an empty placeholder which we'll fill in the
     * EnterCeylon phase later on
     */
    public JCCompilationUnit makeJCCompilationUnitPlaceholder(Tree.CompilationUnit t, JavaFileObject file, String pkgName, PhasedUnit phasedUnit) {
        JCExpression pkg = pkgName != null ? getPackage(pkgName) : null;
        at(t);
        JCCompilationUnit topLev = new CeylonCompilationUnit(List.<JCTree.JCAnnotation> nil(), pkg, List.<JCTree> nil(), null, null, null, null, t, phasedUnit);

        topLev.lineMap = getMap();
        topLev.sourcefile = file;
        topLev.isCeylonProgram = true;

        return topLev;
    }

    /**
     * This runs after _some_ typechecking has been done
     */
    @SuppressWarnings("unchecked")
    public ListBuffer<JCTree> transformAfterTypeChecking(Tree.CompilationUnit t) {
        disableModelAnnotations = false;
        ToplevelAttributesDefinitionBuilder builder = new ToplevelAttributesDefinitionBuilder(this);
        CeylonVisitor visitor = new CeylonVisitor(this, builder);
        t.visitChildren(visitor);
        
        ListBuffer<JCTree> result = ListBuffer.lb();
        result.appendList((ListBuffer<JCTree>) visitor.getResult());
        result.appendList(builder.build());
        
        return result;
    }

    // Make a name from a list of strings, using only the first component.
    Name makeName(Iterable<String> components) {
        Iterator<String> iterator = components.iterator();
        String s = iterator.next();
        assert (!iterator.hasNext());
        return names().fromString(s);
    }

    String toFlatName(Iterable<String> components) {
        StringBuffer buf = new StringBuffer();
        Iterator<String> iterator;

        for (iterator = components.iterator(); iterator.hasNext();) {
            buf.append(iterator.next());
            if (iterator.hasNext())
                buf.append('.');
        }

        return buf.toString();
    }

    // FIXME: port handleOverloadedToplevelClasses when I figure out what it
    // does

    private JCExpression getPackage(String fullname) {
        return makeQuotedQualIdentFromString(fullname);
    }

    public JCImport transform(Tree.ImportPath that) {
        String[] names = new String[that.getIdentifiers().size()];
        int i = 0;
        for (Tree.Identifier component : that.getIdentifiers()) {
            names[i++] = component.getText();
        }
        return at(that).Import(makeQuotedQualIdent(null, names), false);
    }
    
    public List<JCTree> transform(Tree.AnyAttribute decl) {
        return transformAttribute(decl, null);
    }

    public List<JCTree> transform(Tree.AttributeSetterDefinition decl) {
        return transformAttribute(decl, null);
    }
    
    public List<JCTree> transformAttribute(Tree.TypedDeclaration decl, Tree.AttributeSetterDefinition setterDecl) {
        at(decl);
        TypedDeclaration declarationModel = decl.getDeclarationModel(); 
        final String attrName = decl.getIdentifier().getText();
        final String attrClassName = Naming.getAttrClassName(declarationModel, 0);
        final Tree.SpecifierOrInitializerExpression expression;
        final Tree.Block block;
        if (decl instanceof Tree.AttributeDeclaration) {
            Tree.AttributeDeclaration adecl = (Tree.AttributeDeclaration)decl;
            expression = adecl.getSpecifierOrInitializerExpression();
            block = null;
        } else if (decl instanceof Tree.AttributeGetterDefinition) {
            expression = null;
            Tree.AttributeGetterDefinition gdef = (Tree.AttributeGetterDefinition)decl;
            block = gdef.getBlock();
        } else if (decl instanceof Tree.AttributeSetterDefinition) {
            expression = null;
            Tree.AttributeSetterDefinition sdef = (Tree.AttributeSetterDefinition)decl;
            block = sdef.getBlock();
            if (Decl.isLocal(decl)) {
                declarationModel = ((Tree.AttributeSetterDefinition)decl).getDeclarationModel().getParameter();
            }
        } else {
            throw new RuntimeException();
        }
        return transformAttribute(declarationModel, attrName, attrClassName,
                block, expression, setterDecl);
    }

    /** Creates a module class in the package, with the Module annotation required by the runtime. */
    public List<JCTree> transformModuleDescriptor(Tree.ModuleDescriptor module) {
        at(module);
        return ClassDefinitionBuilder
                .klass(this, "module_", null)
                .modifiers(Flags.FINAL)
                .constructorModifiers(Flags.PRIVATE)
                .annotations(makeAtModule(module.getUnit().getPackage().getModule()))
                .build();

    }

    public List<JCTree> transformPackageDescriptor(Tree.PackageDescriptor pack) {
        at(pack);
        return ClassDefinitionBuilder
                .klass(this, "package_", null)
                .modifiers(Flags.FINAL)
                .constructorModifiers(Flags.PRIVATE)
                .annotations(makeAtPackage(pack.getUnit().getPackage()))
                .build();

    }

    public List<JCTree> transformAttribute(
            TypedDeclaration declarationModel,
            String attrName, String attrClassName,
            final Tree.Block block,
            final Tree.SpecifierOrInitializerExpression expression, 
            final Tree.AttributeSetterDefinition setterDecl) {
        
        JCTree.JCExpression initialValue = transformValueInit(
                declarationModel, attrName, expression);
        
        // For captured local variable Values, use a VariableBox
        if (Decl.isBoxedVariable(declarationModel)) {
            List<JCExpression> args = initialValue != null ? List.of(initialValue) : List.<JCExpression>nil();
            JCExpression newBox = make().NewClass(
                    null, List.<JCExpression>nil(), 
                    makeVariableBox(declarationModel), args, null);
            JCTree.JCVariableDecl var = make().VarDef(
                    make().Modifiers(FINAL), 
                    names().fromString(attrClassName), 
                    makeVariableBox(declarationModel), 
                    newBox);
            return List.<JCTree>of(var);
        }
        // For everything else generate a getter/setter method
        AttributeDefinitionBuilder builder = AttributeDefinitionBuilder
            .wrapped(this, attrClassName, attrName, declarationModel, declarationModel.isToplevel())
            .is(Flags.PUBLIC, declarationModel.isShared());
        
        if (declarationModel instanceof Setter
                || declarationModel instanceof Parameter) {
            // For local setters
            JCBlock setterBlock = make().Block(0, statementGen().transformStmts(block.getStatements()));
            builder.setterBlock(setterBlock);
            builder.skipGetter();
        } else {
            if (declarationModel instanceof Value) {
                // For local and toplevel value attributes
                if (!declarationModel.isVariable()) {
                    builder.immutable();
                }
            } else {
                // For local and toplevel getters
                JCBlock getterBlock = make().Block(0, statementGen().transformStmts(block.getStatements()));
                builder.getterBlock(getterBlock);
                
                if (Decl.isLocal(declarationModel)) {
                    // For local getters
                    builder.immutable();
                } else {
                    // For toplevel getters
                    if (setterDecl != null) {
                        JCBlock setterBlock = make().Block(0, statementGen().transformStmts(setterDecl.getBlock().getStatements()));
                        builder.setterBlock(setterBlock);
                    } else {
                        builder.immutable();
                    }
                }
            }
        }
        
        if (Decl.isLocal(declarationModel)) {
            if(initialValue != null)
                builder.valueConstructor();
            return builder.build().append(makeLocalIdentityInstance(attrClassName, 
                    attrClassName, declarationModel.isShared(), initialValue));
        } else {
            if(initialValue != null)
                builder.initialValue(initialValue);
            builder.is(Flags.STATIC, true);
            return builder.build();
        }
    }

    private JCTree.JCExpression transformValueInit(
            TypedDeclaration declarationModel, String attrName,
            final Tree.SpecifierOrInitializerExpression expression) {
        JCTree.JCExpression initialValue = null;
        if (declarationModel instanceof Value) {
            if (expression != null) {
                initialValue = expressionGen().transformExpression(
                        expression.getExpression(), 
                        CodegenUtil.getBoxingStrategy(declarationModel),
                        declarationModel.getType());
            } else {
                Parameter p = CodegenUtil.findParamForDecl(attrName, declarationModel);
                if (p != null) {
                    initialValue = naming.makeName(p, Naming.NA_MEMBER | Naming.NA_ALIASED);
                }
            }
        }
        return initialValue;
    }

    /**
     * Creates a {@code VariableBox<T>}, {@code VariableBoxBoolean}, 
     * {@code VariableBoxLong} etc depending on the given declaration model.
     */
    private JCExpression makeVariableBox(TypedDeclaration declarationModel) {
        JCExpression boxClass;
        boolean unboxed = CodegenUtil.isUnBoxed(declarationModel);
        if (unboxed && isCeylonBoolean(declarationModel.getType())) {
            boxClass = make().Type(syms().ceylonVariableBoxBooleanType);
        } else if (unboxed && isCeylonInteger(declarationModel.getType())) {
            boxClass = make().Type(syms().ceylonVariableBoxLongType);
        } else if (unboxed && isCeylonFloat(declarationModel.getType())) {
            boxClass = make().Type(syms().ceylonVariableBoxDoubleType);
        } else if (unboxed && isCeylonCharacter(declarationModel.getType())) {
            boxClass = make().Type(syms().ceylonVariableBoxIntType);
        } else {
            boxClass = make().Ident(syms().ceylonVariableBoxType.tsym);
            int flags = unboxed ? 0 : JT_TYPE_ARGUMENT;
            boxClass = make().TypeApply(boxClass, 
                    List.<JCExpression>of(
                            makeJavaType(declarationModel.getType(), flags)));
        }
        return boxClass;
    }
}
