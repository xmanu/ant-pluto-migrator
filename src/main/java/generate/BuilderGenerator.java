package generate;

import generate.anthelpers.ReflectionHelpers;
import org.apache.tools.ant.*;
import org.apache.tools.ant.taskdefs.Javac;
import org.apache.tools.ant.types.EnumeratedAttribute;
import sun.lwawt.macosx.CImage;
import utils.ReflectionUtils;
import utils.StringUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by manuel on 29.11.16.
 */
public class BuilderGenerator extends JavaGenerator {
    private final String name;

    private final Project project;
    private final String projectName;
    private final Boolean useFileDependencyDiscovery;
    private List<String> dependentBuilders = new ArrayList<>();
    private List<String> dependentFiles = new ArrayList<>();
    private List<Task> tasks = new ArrayList<>();

    private final NamingManager namingManager = new NamingManager();
    private final PropertyResolver resolver;



    //<editor-fold desc="Getters and Setters" defaultstate="collapsed">
    public List<String> getDependentBuilders() {
        return dependentBuilders;
    }

    public void setDependentBuilders(List<String> dependentBuilders) {
        this.dependentBuilders = dependentBuilders;
    }

    public List<String> getDependentFiles() {
        return dependentFiles;
    }

    public void setDependentFiles(List<String> dependentFiles) {
        this.dependentFiles = dependentFiles;
    }

    public List<Task> getTasks() {
        return tasks;
    }

    public void setTasks(List<Task> tasks) {
        this.tasks = tasks;
    }

    public String getName() {
        return name;
    }

    public Boolean getUseFileDependencyDiscovery() {
        return useFileDependencyDiscovery;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getInputName() {
        return getProjectName() + "Input";
    }

    public NamingManager getNamingManager() {
        return namingManager;
    }
    //</editor-fold>

    public BuilderGenerator(String pkg, String name, Project project, Boolean useFileDependencyDiscovery) {
        super(pkg);
        this.name = getNamingManager().getClassNameFor(StringUtils.capitalize(name));
        this.project = project;
        this.projectName = getNamingManager().getClassNameFor(StringUtils.capitalize(project.getName()));
        this.useFileDependencyDiscovery = useFileDependencyDiscovery;
        this.resolver = new PropertyResolver(project, "input");
    }

    private void generateBuildMethod() {
        this.addImport("java.io.IOException");
        this.printString("@Override\n" +
                "protected None build(" + this.getInputName() + " input) throws Exception {", "}");
        this.increaseIndentation(1);

        for (String fileDep : getDependentFiles()) {
            this.printString("require(new File(\"" + fileDep + "\"));");
        }

        for (String dep : getDependentBuilders()) {
            String depName = StringUtils.capitalize(getNamingManager().getClassNameFor(dep));
            this.printString(this.getInputName() + " " + StringUtils.decapitalize(depName) + "Input = new " + this.getInputName() + "();");
            this.printString("requireBuild(" + depName + "Builder.factory, " + StringUtils.decapitalize(depName) + "Input);");
        }

        addImport("org.apache.tools.ant.Project");
        printString("Project project = new Project();");
        printString("project.addBuildListener(new PlutoBuildListener());");
        for (Task t : getTasks()) {
            if (t instanceof UnknownElement) {
                UnknownElement element = (UnknownElement) t;

                String taskName = getNamingManager().getNameFor(StringUtils.decapitalize(element.getTaskName()));

                generateElement(taskName, element, null, false);

                if (!element.getTaskName().equals("antcall"))
                    this.printString(taskName + ".execute();");
            } else {
                throw new RuntimeException("Didn't know how to handle " + t.toString());
                // TODO: Deal with non UnknownElements.
            }
            // TODO: task
        }

        this.printString("return None.val;");
        this.closeOneLevel();
    }

    private void generateElement(String taskName, UnknownElement element, Class<?> elementTypeClass, boolean noConstructor) {

        ComponentHelper componentHelper = ComponentHelper.getComponentHelper(project);


        if (element.getTaskName().equals("antcall")) {
            // Deal with antcalls

            String depName = StringUtils.capitalize(getNamingManager().getClassNameFor(element.getWrapper().getAttributeMap().get("target").toString()));
            this.printString(this.getInputName() + " " + StringUtils.decapitalize(depName) + "Input = new " + this.getInputName() + "();");
            this.printString("requireBuild(" + depName + "Builder.factory, " + StringUtils.decapitalize(depName) + "Input);");

            return;
        }

        try {
            if (elementTypeClass == null) {
                AntTypeDefinition typeDefinition = componentHelper.getDefinition(element.getTaskName());
                typeDefinition = componentHelper.getDefinition(element.getTaskName());
                elementTypeClass = typeDefinition.getTypeClass(project);
                if (elementTypeClass == null)
                    throw new RuntimeException("Could not get type definition for " + element.getTaskName());
            }
        } catch (NullPointerException e) {
            throw new RuntimeException("Could not get type definition for " + element.getTaskName());
        }

        //String taskName = getNamingManager().getNameFor(StringUtils.decapitalize(element.getTaskName()));

        final IntrospectionHelper introspectionHelper = IntrospectionHelper.getHelper(elementTypeClass);

        Constructor<?> constructor = null;
        try {
            IntrospectionHelper.Creator creator = introspectionHelper.getElementCreator(project, "", null, element.getTaskName(), element);
            constructor = ReflectionHelpers.getNestedCreatorConstructorFor(creator);
        } catch (NullPointerException e) {

        }
        if (constructor == null) {
            if (!noConstructor) {
                String fullyQualifiedTaskdefName = elementTypeClass.getCanonicalName();
                addImport(fullyQualifiedTaskdefName);

                String taskClassName = fullyQualifiedTaskdefName.substring(fullyQualifiedTaskdefName.lastIndexOf(".") + 1);

                this.printString(taskClassName + " " + taskName + " = new " + taskClassName + "();");
            }
        } else {
            // TODO: We have a contructor
            System.out.println("CONSTRUCTOR!: " + constructor.toGenericString());

            String fullyQualifiedTaskdefName = elementTypeClass.getCanonicalName();
            addImport(fullyQualifiedTaskdefName);

            String taskClassName = fullyQualifiedTaskdefName.substring(fullyQualifiedTaskdefName.lastIndexOf(".") + 1);

            this.printString(taskClassName + " " + taskName + " = new " + taskClassName + "();");
        }
        boolean hasProjectSetter = false;
        for (Method method: elementTypeClass.getMethods()) {
            if (method.getName().equals("setProject") && method.getParameterCount() == 1 && method.getParameterTypes()[0].getName().equals("org.apache.tools.ant.Project")) {
                hasProjectSetter = true;
                break;
            }
        }
        if (hasProjectSetter)
            this.printString(taskName + ".setProject(project);");

        if (element.getWrapper().getAttributeMap().contains("id")) {
            // We have a reference id. Add code to add it to the project.
            this.printString("project.addReference(\""+element.getWrapper().getAttributeMap().get("id")+"\", " + taskName + ");");
        }

        try {
            element.maybeConfigure();
        }
        catch (Throwable t) {

        }

        element.getWrapper().getAttributeMap().forEach((n, o) ->
                {
                    Method attributeMethod = introspectionHelper.getAttributeMethod(n.toLowerCase());

                    String setter = attributeMethod.getName();

                    // Get type of argument
                    Class<?> argumentClass = introspectionHelper.getAttributeType(n.toLowerCase());

                    String argument = StringUtils.javaPrint(o.toString());
                    if (argumentClass.getName().equals("boolean")) {
                        // We expect a boolean, use true or false as values without wrapping into a string.
                        argument = "Boolean.valueOf(\"" + resolver.getExpandedValue(o.toString()) + "\")";
                    } else if (EnumeratedAttribute.class.isAssignableFrom(argumentClass)) {
                        String completeClassName = argumentClass.getCanonicalName();
                        String shortName = argumentClass.getSimpleName();
                        String attrName = getNamingManager().getNameFor(shortName);
                        this.printString(completeClassName + " " + attrName + " = new " + completeClassName + "();");
                        this.printString(attrName + ".setValue(\"" + o.toString() + "\");");
                    } else if (!(argumentClass.getName().equals("java.lang.String")|| argumentClass.getName().equals("java.lang.Object"))) {

                        boolean includeProject;
                        Constructor<?> c;
                        try {
                            // First try with Project.
                            c = argumentClass.getConstructor(Project.class, String.class);
                            includeProject = true;
                        } catch (final NoSuchMethodException nme) {
                            // OK, try without.
                            try {
                                c = argumentClass.getConstructor(String.class);
                                includeProject = false;
                            } catch (final NoSuchMethodException nme2) {
                                // Well, no matching constructor.
                                throw new RuntimeException("We didn't find any matching constructor for type " + argumentClass.toString());
                            }
                        }

                        addImport(argumentClass.getName());

                        // Not a string. Use single argument constructor from single string...
                        // This might not exist resulting in a type error in the resulting migrated Script
                        if (includeProject) {
                            argument = "new " + argumentClass.getSimpleName() + "(project, " + resolver.getExpandedValue(argument) + ")";
                        } else {
                            argument = "new " + argumentClass.getSimpleName() + "(" + resolver.getExpandedValue(argument) + ")";
                        }
                    }

                    this.printString(taskName + "." + setter + "(" + resolver.getExpandedValue(argument) + ");");
                }
        );

        if (element.getChildren() != null) {
            for (UnknownElement child: element.getChildren()) {
                if (introspectionHelper.supportsNestedElement("", child.getTaskName())) {
                    IntrospectionHelper.Creator ccreator = introspectionHelper.getElementCreator(project, "", null, child.getTaskName(), child);
                    Method method = ReflectionHelpers.getNestedCreatorMethodFor(ccreator);
                    Constructor<?> cconstructor = ReflectionHelpers.getNestedCreatorConstructorFor(ccreator);
                    if (method != null) {
                        String childName = getNamingManager().getNameFor(StringUtils.decapitalize(child.getTaskName()));
                        if (method.getAnnotatedReturnType().getType().getTypeName().equals("void")) {
                            if (cconstructor != null) {
                                Class<?> cls = null;
                                try {
                                    cls = Class.forName(cconstructor.getAnnotatedReturnType().getType().getTypeName());
                                } catch (ClassNotFoundException e) {
                                    e.printStackTrace();
                                }
                                generateElement(childName, child, cls, false);
                            }
                            else
                                generateElement(childName, child, null, false);
                            this.printString(taskName + "." + method.getName() + "(" + childName + ");");
                        } else {
                            this.printString(method.getAnnotatedReturnType().getType().getTypeName().replace("$", ".") + " " + childName + " = " + taskName + "." + method.getName() + "();");

                            String returnTypeName = method.getReturnType().getName();

                            Class<?> cls = null;
                            try {
                                cls = Class.forName(returnTypeName);
                            } catch (ClassNotFoundException e) {
                                e.printStackTrace();
                            }

                            generateElement(childName, child, cls, true);
                        }

                    } else {
                        throw new RuntimeException("Unexpected exception inspecting ant framework...");
                    }
                } else {
                    throw new RuntimeException("Didn't support nested element: " + child.getTaskName());
                }
            }
        }
    }

    private void generateClass() {
        this.addImport("build.pluto.builder.Builder");
        this.addImport("build.pluto.output.None");
        this.printString("public class " + getName() + " extends Builder<" + this.getProjectName() + "Input, None> {", "}");
        this.increaseIndentation(1);
        this.addImport("build.pluto.builder.factory.BuilderFactory");
        this.addImport("build.pluto.builder.factory.BuilderFactoryFactory");
        this.printString("public static BuilderFactory<" + this.getProjectName() + "Input, None, " + getName() + "> factory = BuilderFactoryFactory.of(" + getName() + ".class, " + this.getProjectName() + "Input.class);");

        //generateInputClass();

        this.printString("public " + getName() + "(" + this.getProjectName() + "Input input) { super(input); }");

        this.printString("@Override\n" +
                "protected String description(" + this.getProjectName() + "Input input) {\n" +
                "  return \"Builder " + getName() + ": \" + input;\n" +
                "}");

        this.addImport("java.io.File");
        this.printString("@Override\n" +
                "public File persistentPath(" + this.getProjectName() + "Input input) {\n" +
                "  return new File(\"deps/" + getName() + ".dep\");\n" +
                "}");

        this.addImport("build.pluto.stamp.Stamper");
        this.addImport("build.pluto.stamp.FileHashStamper");
        this.printString("@Override\n" +
                "protected Stamper defaultStamper() {\n" +
                "  return FileHashStamper.instance;\n" +
                "}");


        generateBuildMethod();

        if (useFileDependencyDiscovery)
            generateUseFileDependencyDiscoveryPrettyPrint();
        this.closeOneLevel();
    }


    private void generateUseFileDependencyDiscoveryPrettyPrint() {
        this.printString("@Override\n" +
                "protected boolean useFileDependencyDiscovery() {\n" +
                "  return " + getUseFileDependencyDiscovery().toString() + ";\n" +
                "}");
    }

    public void generatePrettyPrint() {
        super.generatePrettyPrint();
        generateClass();
    }
}
