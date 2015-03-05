package com.atlassian.clover.instr.aspectj;

import com.atlassian.clover.api.instrumentation.InstrumentationSession;
import com.atlassian.clover.api.registry.MethodInfo;
import com.atlassian.clover.context.ContextSet;
import com.atlassian.clover.registry.FixedSourceRegion;
import com.atlassian.clover.registry.entities.FullStatementInfo;
import com.atlassian.clover.registry.entities.MethodSignature;
import com.atlassian.clover.registry.entities.Modifier;
import com.atlassian.clover.registry.entities.Modifiers;
import com.atlassian.clover.registry.entities.Parameter;
import com.atlassian.clover.spi.lang.LanguageConstruct;
import com.atlassian.clover.util.collections.Pair;
import org.aspectj.ajdt.internal.compiler.ast.DeclareDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.ASTVisitor;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.Assignment;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.CaseStatement;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.DoStatement;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.ForStatement;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.ForeachStatement;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.IfStatement;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.LabeledStatement;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.LocalDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.MessageSend;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.MethodDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.ReturnStatement;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.Statement;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.SwitchStatement;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.TryStatement;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.WhileStatement;
import org.aspectj.org.eclipse.jdt.internal.compiler.lookup.BlockScope;
import org.aspectj.org.eclipse.jdt.internal.compiler.lookup.ClassScope;
import org.aspectj.org.eclipse.jdt.internal.compiler.lookup.CompilationUnitScope;

import java.io.File;

/**
 *
 */
public class CloverAjAstInstrumenter extends ASTVisitor {

    private final InstrumentationSession session;

    private CharToLineColMapper lineColMapper;

    public CloverAjAstInstrumenter(InstrumentationSession session) {
        this.session = session;
    }

    // instrument file

    @Override
    public boolean visit(CompilationUnitDeclaration compilationUnitDeclaration, CompilationUnitScope scope) {
        // parse current source file and gather information about line endings
        lineColMapper = new CharToLineColMapper(new File(new String(compilationUnitDeclaration.getFileName())));

        File sourceFile = new File(new String(compilationUnitDeclaration.getFileName()));
        session.enterFile(
                "introduction", // TODO get a true pkg name
                sourceFile,
                lineColMapper.getLineCount(),
                lineColMapper.getLineCount(),
                sourceFile.lastModified(),
                sourceFile.length(),
                0
        );
        return super.visit(compilationUnitDeclaration, scope);
    }

    @Override
    public void endVisit(CompilationUnitDeclaration compilationUnitDeclaration, CompilationUnitScope scope) {
        super.endVisit(compilationUnitDeclaration, scope);
        // parse current source file and gather information about line endings
        session.exitFile();
    }
    // instrument top-level class

    @Override
    public boolean visit(TypeDeclaration typeDeclaration, CompilationUnitScope scope) {
        final Pair<Integer, Integer> lineCol = charIndexToLineCol(typeDeclaration.declarationSourceStart);
        session.enterClass(
                new String(typeDeclaration.name),
                new FixedSourceRegion(lineCol.first, lineCol.second),
                Modifiers.createFrom(Modifier.PUBLIC, null), // TODO convert from ClassFileConstants
                false,
                false,
                false);
        return super.visit(typeDeclaration, scope);
    }

    @Override
    public void endVisit(TypeDeclaration typeDeclaration, CompilationUnitScope scope) {
        super.endVisit(typeDeclaration, scope);
        Pair<Integer, Integer> lineCol = charIndexToLineCol(typeDeclaration.declarationSourceEnd);
        session.exitClass(lineCol.first, lineCol.second);
    }

    // instrument methods

    @Override
    public boolean visit(MethodDeclaration methodDeclaration, ClassScope scope) {
        final Pair<Integer, Integer> lineCol = charIndexToLineCol(methodDeclaration.declarationSourceStart);
        final MethodInfo methodInfo = session.enterMethod(
                new ContextSet(),
                new FixedSourceRegion(lineCol.first, lineCol.second),
                extractMethodSignature(methodDeclaration),
                methodDeclaration instanceof DeclareDeclaration,  // hack: 'declare' treat as test method to display a friendly name
                methodDeclaration instanceof DeclareDeclaration
                        ? ((DeclareDeclaration) methodDeclaration).declareDecl.toString() // hack: use static test name for 'declare'
                        : null,  // no static test name
                false,
                1,
                LanguageConstruct.Builtin.METHOD);
        int index = methodInfo.getDataIndex();

        // TODO REWRITE THE NODE

        return super.visit(methodDeclaration, scope);
    }

    @Override
    public void endVisit(MethodDeclaration methodDeclaration, ClassScope scope) {
        super.endVisit(methodDeclaration, scope);
        Pair<Integer, Integer> lineCol = charIndexToLineCol(methodDeclaration.declarationSourceEnd);
        session.exitMethod(lineCol.first, lineCol.second);
    }

    // instrument statements

    // TODO Expression (?), LabeledStatement (?)

    /**
     * Variable assignment, e.g. "i = 2"
     */
    @Override
    public void endVisit(Assignment assignment, BlockScope scope) {
        endVisitStatement(assignment, scope);
        super.endVisit(assignment, scope);
    }

    /**
     * Case block
     */
    @Override
    public void endVisit(CaseStatement statement, BlockScope scope) {
        endVisitStatement(statement, scope);
        super.endVisit(statement, scope);
    }

    /**
     * Do-while loop
     */
    @Override
    public void endVisit(DoStatement statement, BlockScope scope) {
        endVisitStatement(statement, scope);
        super.endVisit(statement, scope);
    }

    /**
     * For statement, e.g. "for (a=0; a < b; a++)"
     */
    @Override
    public void endVisit(ForStatement statement, BlockScope scope) {
        endVisitStatement(statement, scope);
        super.endVisit(statement, scope);
    }

    /**
     * Foreach statement, e.g. "for (line : lines)"
     */
    @Override
    public void endVisit(ForeachStatement statement, BlockScope scope) {
        endVisitStatement(statement, scope);
        super.endVisit(statement, scope);
    }

    /**
     * If statement, e.g. "if (a < b) ..."
     */
    @Override
    public void endVisit(IfStatement statement, BlockScope scope) {
        endVisitStatement(statement, scope);
        super.endVisit(statement, scope);
    }

    /**
     * Local variable declaration, e.g. "Foo f = new Foo();"
     */
    @Override
    public void endVisit(LocalDeclaration localDeclaration, BlockScope scope) {
        endVisitStatement(localDeclaration, scope);
        super.endVisit(localDeclaration, scope);
    }

    /**
     * Simple method call, e.g. "foo();"
     */
    @Override
    public void endVisit(MessageSend messageSend, BlockScope scope) {
        endVisitStatement(messageSend, scope);
        super.endVisit(messageSend, scope);
    }

    /**
     * Switch statement
     */
    @Override
    public void endVisit(SwitchStatement assignment, BlockScope scope) {
        endVisitStatement(assignment, scope);
        super.endVisit(assignment, scope);
    }

    /**
     * Return statement, e.g. "return 2"
     */
    @Override
    public void endVisit(ReturnStatement assignment, BlockScope scope) {
        endVisitStatement(assignment, scope);
        super.endVisit(assignment, scope);
    }

    /**
     * Return statement, e.g. "return 2"
     */
    @Override
    public void endVisit(TryStatement assignment, BlockScope scope) {
        endVisitStatement(assignment, scope);
        super.endVisit(assignment, scope);
    }

    /**
     * While loop
     */
    @Override
    public void endVisit(WhileStatement statement, BlockScope scope) {
        endVisitStatement(statement, scope);
        super.endVisit(statement, scope);
    }

    // helper methods

    protected void endVisitStatement(Statement genericStatement, BlockScope blockScope) {
        Pair<Integer, Integer> lineColStart = charIndexToLineCol(genericStatement.sourceStart);
        Pair<Integer, Integer> lineColEnd = charIndexToLineCol(genericStatement.sourceEnd);
        FullStatementInfo statementInfo = session.addStatement(
                new ContextSet(),
                new FixedSourceRegion(
                        lineColStart.first, lineColStart.second,
                        lineColEnd.first, lineColEnd.second),
                1,
                LanguageConstruct.Builtin.STATEMENT);
        int index = statementInfo.getDataIndex();

        // TODO REWRITE NODE

    }

    protected MethodSignature extractMethodSignature(MethodDeclaration methodDeclaration) {
        // TODO create a full method signature (annotations, modifiers etc)
        return new MethodSignature(
                new String(methodDeclaration.selector),
                null,
                methodDeclaration.returnType.toString(),
                new Parameter[0],
                new String[0],
                Modifiers.createFrom(Modifier.PUBLIC, null));
    }

    protected Pair<Integer, Integer> charIndexToLineCol(int charIndex) {
        // convert char index to line:column
        if (lineColMapper != null) {
            return lineColMapper.getLineColFor(charIndex);
        } else {
            throw new IllegalStateException("lineColMapper has not been initialized!");
        }
    }
}
