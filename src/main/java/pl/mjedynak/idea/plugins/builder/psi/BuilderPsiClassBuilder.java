package pl.mjedynak.idea.plugins.builder.psi;

import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import org.apache.commons.lang.StringUtils;
import pl.mjedynak.idea.plugins.builder.settings.CodeStyleSettings;
import pl.mjedynak.idea.plugins.builder.writer.BuilderContext;

import java.util.List;

public class BuilderPsiClassBuilder {

    private static final String PRIVATE_STRING = "private";
    private static final String SPACE = " ";
    private static final String SEMICOLON = ",";
    static final String STATIC_MODIFIER = "static";

    private PsiHelper psiHelper = new PsiHelper();
    private PsiFieldsModifier psiFieldsModifier = new PsiFieldsModifier();
    private CodeStyleSettings codeStyleSettings = new CodeStyleSettings();
    private MethodCreator methodCreator;

    private PsiClass srcClass = null;
    private String builderClassName = null;

    private List<PsiField> psiFieldsForSetters = null;
    private List<PsiField> psiFieldsForConstructor = null;

    private PsiClass builderClass = null;
    private PsiElementFactory elementFactory = null;
    private String srcClassName = null;
    private String srcClassFieldName = null;

    public BuilderPsiClassBuilder aBuilder(BuilderContext context) {
        initializeFields(context);
        JavaDirectoryService javaDirectoryService = psiHelper.getJavaDirectoryService();
        builderClass = javaDirectoryService.createClass(context.getTargetDirectory(), builderClassName);
        return this;
    }

    public BuilderPsiClassBuilder anInnerBuilder(BuilderContext context) {
        initializeFields(context);
        builderClass = elementFactory.createClass(builderClassName);
        builderClass.getModifierList().setModifierProperty(STATIC_MODIFIER, true);
        return this;
    }

    private void initializeFields(BuilderContext context) {
        JavaPsiFacade javaPsiFacade = psiHelper.getJavaPsiFacade(context.getProject());
        elementFactory = javaPsiFacade.getElementFactory();
        srcClass = context.getPsiClassFromEditor();
        builderClassName = context.getClassName();
        srcClassName = context.getPsiClassFromEditor().getName();
        srcClassFieldName = StringUtils.uncapitalize(srcClassName);
        psiFieldsForSetters = context.getPsiFieldsForBuilder().getFieldsForSetters();
        psiFieldsForConstructor = context.getPsiFieldsForBuilder().getFieldsForConstructor();
        methodCreator = new MethodCreator(elementFactory, builderClassName);
    }

    public BuilderPsiClassBuilder withFields() {
        psiFieldsModifier.modifyFields(psiFieldsForSetters, psiFieldsForConstructor, builderClass);
        return this;
    }

    public BuilderPsiClassBuilder withPrivateConstructor() {
        PsiMethod constructor = elementFactory.createConstructor();
        constructor.getModifierList().setModifierProperty(PRIVATE_STRING, true);
        builderClass.add(constructor);
        return this;
    }

    public BuilderPsiClassBuilder withInitializingMethod() {
        PsiMethod staticMethod = elementFactory.createMethodFromText(
                "public static " + builderClassName + SPACE + StringUtils.uncapitalize(builderClassName) + "() { return new " + builderClassName + "();}", srcClass);
        builderClass.add(staticMethod);
        return this;
    }

    public BuilderPsiClassBuilder withSetMethods(String methodPrefix) {
        for (PsiField psiFieldForSetter : psiFieldsForSetters) {
            createAndAddMethod(psiFieldForSetter, methodPrefix);
        }
        for (PsiField psiFieldForConstructor : psiFieldsForConstructor) {
            createAndAddMethod(psiFieldForConstructor, methodPrefix);
        }
        return this;
    }

    private void createAndAddMethod(PsiField psiField, String methodPrefix) {
        builderClass.add(methodCreator.createMethod(psiField, methodPrefix));
    }

    public PsiClass build() {
        StringBuilder buildMethodText = new StringBuilder();
        appendConstructor(buildMethodText);
        appendSetMethods(buildMethodText);
        buildMethodText.append("return ").append(srcClassFieldName).append(";}");
        PsiMethod buildMethod = elementFactory.createMethodFromText(buildMethodText.toString(), srcClass);
        builderClass.add(buildMethod);
        return builderClass;
    }

    private void appendConstructor(StringBuilder buildMethodText) {
        String constructorParameters = createConstructorParameters();
        buildMethodText.append("public ").append(srcClassName).append(" build() { ").append(srcClassName).append(SPACE)
                .append(srcClassFieldName).append(" = new ").append(srcClassName).append("(").append(constructorParameters).append(");");
    }

    private void appendSetMethods(StringBuilder buildMethodText) {
        for (PsiField psiFieldsForSetter : psiFieldsForSetters) {
            String fieldNamePrefix = codeStyleSettings.getFieldNamePrefix();
            String fieldName = psiFieldsForSetter.getName();
            String fieldNameWithoutPrefix = fieldName.replaceFirst(fieldNamePrefix, "");
            String fieldNameUppercase = StringUtils.capitalize(fieldNameWithoutPrefix);
            buildMethodText.append(srcClassFieldName).append(".set").append(fieldNameUppercase).append("(").append(fieldName).append(");");
        }
    }

    private String createConstructorParameters() {
        StringBuilder sb = new StringBuilder();
        for (PsiField psiField : psiFieldsForConstructor) {
            sb.append(psiField.getName()).append(SEMICOLON);
        }
        removeLastSemicolon(sb);
        return sb.toString();
    }

    private void removeLastSemicolon(StringBuilder sb) {
        if (sb.toString().endsWith(SEMICOLON)) {
            sb.deleteCharAt(sb.length() - 1);
        }
    }
}
