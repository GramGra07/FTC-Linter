package com.gentrifiedapps.ftc_intellij_plugin.utils;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;

public class OpModeClassUtils {
    private OpModeClassUtils() {
    }

    public static PsiDirectory isCreateActionValid(AnActionEvent e) {
        // Check project
        Project project = e.getProject();
        if(project == null) return null;

        // Check if directory was clicked
        PsiDirectory directory = e.getData(CommonDataKeys.PSI_ELEMENT) instanceof PsiDirectory ? (PsiDirectory) e.getData(CommonDataKeys.PSI_ELEMENT) : null;
        if(directory == null) return null;

        // Check if directory was a Java package
        PsiPackage javaPackage = JavaDirectoryService.getInstance().getPackage(directory);
        if(javaPackage == null) return null;

        return directory;
    }

    public static PsiClass createOpModeClass(PsiDirectory directory, String opModeName) {
        return JavaDirectoryService.getInstance().createClass(directory, opModeName);
    }

    public static Runnable fillOpModeClass(Project project, String opModeName, PsiClass opModeClass, String teleopAnnotation) {
        return () -> {
            PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();

            // Add imports
            opModeClass.addBefore(
                    elementFactory.createImportStatementOnDemand("com.qualcomm.robotcore.eventloop.opmode"),
                    opModeClass
            );

            // Extend LinearOpMode
            PsiJavaCodeReferenceElement superClassReference = elementFactory.createReferenceFromText("LinearOpMode", opModeClass);
            opModeClass.getExtendsList().add(superClassReference);

            // Annotate class
            PsiAnnotation teleOpAnnotation = elementFactory.createAnnotationFromText(
                    "@" + teleopAnnotation + "(name=\"" + opModeName + "\")",
                    opModeClass
            );
            opModeClass.addBefore(teleOpAnnotation, opModeClass.getFirstChild());

            // Create runOpMode method
            PsiMethod runOpMode = elementFactory.createMethod("runOpMode", PsiTypes.voidType());
            PsiAnnotation overrideAnnotation = elementFactory.createAnnotationFromText("@Override", runOpMode);
            runOpMode.addBefore(overrideAnnotation, runOpMode.getFirstChild());

            // Add wait and a single loop/guard statement based on preKeyword
            PsiCodeBlock runOpModeBody = runOpMode.getBody();
            runOpModeBody.add(elementFactory.createStatementFromText("waitForStart();", runOpMode));
            String preKeyword;
            if (teleopAnnotation.equals("Autonomous")) {
                preKeyword = "if";
            } else {
                preKeyword = "while";
            }
            String kw = preKeyword.trim();
            PsiStatement stmt = elementFactory.createStatementFromText(kw + " (opModeIsActive()) {}", runOpMode);
            PsiStatement added = (PsiStatement) runOpModeBody.add(stmt);

            // Add comment inside the created block
            if (added instanceof PsiIfStatement psiIf) {
                PsiStatement thenBranch = psiIf.getThenBranch();
                if (thenBranch instanceof PsiBlockStatement blockStmt) {
                    blockStmt.getCodeBlock().add(elementFactory.createCommentFromText("// OpMode loop", null));
                }
            } else if (added instanceof PsiWhileStatement psiWhile) {
                PsiStatement body = psiWhile.getBody();
                if (body instanceof PsiBlockStatement blockStmt) {
                }
            }

            // Add to class
            opModeClass.add(runOpMode);
        };
    }
}