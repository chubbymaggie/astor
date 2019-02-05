package fr.inria.astor.approaches.tos.operator.metaevaltos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import fr.inria.astor.approaches.cardumen.FineGrainedExpressionReplaceOperator;
import fr.inria.astor.approaches.tos.core.InsertMethodOperator;
import fr.inria.astor.core.entities.Ingredient;
import fr.inria.astor.core.entities.ModificationPoint;
import fr.inria.astor.core.entities.OperatorInstance;
import fr.inria.astor.core.entities.StatementOperatorInstance;
import fr.inria.astor.core.entities.meta.MetaOperator;
import fr.inria.astor.core.entities.meta.MetaOperatorInstance;
import fr.inria.astor.core.manipulation.MutationSupporter;
import fr.inria.astor.core.manipulation.sourcecode.VariableResolver;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtCatch;
import spoon.reflect.code.CtCodeSnippetExpression;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtIf;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtTry;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.cu.position.NoSourcePosition;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.code.CtBlockImpl;
import spoon.support.reflect.code.CtReturnImpl;

/**
 * 
 * @author Matias Martinez
 *
 */
public class VarReplacementByMethodCallOp extends FineGrainedExpressionReplaceOperator implements MetaOperator {

	@Override
	public List<MetaOperatorInstance> createMetaOperatorInstances(ModificationPoint modificationPoint) {

		// Let's create one meta per modif point
		List<OperatorInstance> metaOperations = new ArrayList();
		MetaOperatorInstance opMega = new MetaOperatorInstance(metaOperations);
		List<MetaOperatorInstance> opsMega = new ArrayList();

		opsMega.add(opMega);

		CtType<?> target = modificationPoint.getCodeElement().getParent(CtType.class);
		Set<ModifierKind> modifiers = new HashSet<>();
		modifiers.add(ModifierKind.PRIVATE);
		// modifiers.add(ModifierKind.STATIC);

		Map<Integer, Ingredient> ingredientOfMapped = new HashMap<>();

		//
		List<CtVariableAccess> varAccessInModificationPoints = VariableResolver
				.collectVariableAccess(modificationPoint.getCodeElement(), true);

		log.debug("\nModifcationPoint: \n" + modificationPoint);

		// let's start with one, and let's keep the Zero for the default (all ifs are
		// false)
		// TODO: we only can activate one mutant
		int candidateNumber = 0;

		int variableCounter = 0;
		for (CtVariableAccess variableAccessToReplace : varAccessInModificationPoints) {

			List<Ingredient> ingredients = this.computeIngredientsFromVarToReplace(modificationPoint,
					variableAccessToReplace);

			if (ingredients.isEmpty()) {
				continue;
			}

			// The parameters to be included in the new method
			List<CtVariableAccess> varsToBeParameters = ingredients.stream()
					.map(e -> e.getCode().getElements(new TypeFilter<>(CtVariableAccess.class))).flatMap(List::stream)
					.map(CtVariableAccess.class::cast).collect(Collectors.toList());

			// The variable to be replaced must also be a parameter

			varsToBeParameters.add(variableAccessToReplace);

			// List of parameters
			List<CtParameter<?>> parameters = new ArrayList<>();
			List<CtExpression<?>> realParameters = new ArrayList<>();
			for (CtVariableAccess ctVariableAccess : varsToBeParameters) {
				// the parent is null, it is setter latter
				CtParameter pari = MutationSupporter.getFactory().createParameter(null, ctVariableAccess.getType(),
						ctVariableAccess.getVariable().getSimpleName());
				parameters.add(pari);
				realParameters.add(ctVariableAccess.clone().setPositions(new NoSourcePosition()));
			}

			variableCounter++;
			String name = "_meta_" + variableCounter;

			Set<CtTypeReference<? extends Throwable>> thrownTypes = new HashSet<>();

			CtExpression thisTarget = MutationSupporter.getFactory().createTypeAccess(target.getReference());

			// The return type of the new method correspond to the type of variable to
			// change
			CtTypeReference returnType = variableAccessToReplace.getType();

			CtMethod<?> megaMethod = MutationSupporter.getFactory().createMethod(target, modifiers, returnType, name,
					parameters, thrownTypes);

			;
			CtInvocation newInvocationToMega = MutationSupporter.getFactory().createInvocation(// MutationSupporter.getFactory().createThisAccess()
					thisTarget, megaMethod.getReference(), realParameters);

			/// Let's start creating the body of the new method.
			// first the main try
			CtTry tryMethodMain = MutationSupporter.getFactory().createTry();
			List<CtCatch> catchers = new ArrayList<>();
			CtCatch catch1 = MutationSupporter.getFactory().createCtCatch("e", Exception.class, new CtBlockImpl());
			catchers.add(catch1);
			tryMethodMain.setCatchers(catchers);
			CtBlock tryBoddy = new CtBlockImpl();
			tryMethodMain.setBody(tryBoddy);

			CtBlock methodBodyBlock = new CtBlockImpl();
			megaMethod.setBody(methodBodyBlock);
			methodBodyBlock.addStatement(tryMethodMain);

			for (Ingredient ingredientCandidate : ingredients) {

				candidateNumber++;
				CtExpression expressionCandidate = (CtExpression) ingredientCandidate.getCode();
				CtCodeSnippetExpression caseCondition = MutationSupporter.getFactory().createCodeSnippetExpression(
						"\"" + candidateNumber + "\".equals(System.getProperty(\"mutnumber\")) ");

				ingredientOfMapped.put(candidateNumber, ingredientCandidate);

				CtIf particularIf = MutationSupporter.getFactory().createIf();
				particularIf.setCondition(caseCondition);
				CtStatement stPrint = MutationSupporter.getFactory().createCodeSnippetStatement(
						"System.out.println(" + "\"\\nPROPERTY met:\" +System.getProperty(\"mutnumber\"))");
				CtBlock particularIfBlock = new CtBlockImpl<>();
				particularIfBlock.addStatement(stPrint);
				particularIf.setThenStatement(particularIfBlock);

				// The return inside the if
				// add a return with the expression
				CtReturn casereturn = new CtReturnImpl<>();
				casereturn.setReturnedExpression(expressionCandidate);
				particularIfBlock.addStatement(casereturn);

				// Add the if tho the methodBlock
				// methodBodyBlock
				tryBoddy.addStatement(particularIf);

			}

			// By default, return the original
			CtReturn defaultReturnLast = new CtReturnImpl<>();
			CtExpression expCloned = variableAccessToReplace.clone();
			expCloned.setPosition(new NoSourcePosition());
			MutationSupporter.clearPosition(expCloned);

			defaultReturnLast.setReturnedExpression(expCloned);
			methodBodyBlock.addStatement(defaultReturnLast);

			/// ***
			// Up to here, the different cases

			// Now the if to be inserted:
			// 1:

			CtElement elementSource = variableAccessToReplace;
			// MutationSupporter.clearPosition(statementPointedCloned);

			// Let's create the operations

			OperatorInstance opInvocation = new OperatorInstance();
			opInvocation.setOperationApplied(new FineGrainedExpressionReplaceOperator());
			opInvocation.setOriginal(elementSource);
			opInvocation.setModified(newInvocationToMega);
			opInvocation.setModificationPoint(modificationPoint);

			metaOperations.add(opInvocation);

			// 2:
			// The meta method to be added
			OperatorInstance opMethodAdd = new OperatorInstance();
			opMethodAdd.setOperationApplied(new InsertMethodOperator());
			opMethodAdd.setOriginal(modificationPoint.getCodeElement());
			opMethodAdd.setModified(megaMethod);
			opMethodAdd.setModificationPoint(modificationPoint);
			metaOperations.add(opMethodAdd);

			//
			log.debug("method: \n" + megaMethod);

			log.debug("invocation: \n" + newInvocationToMega);

		} // End variable

		opMega.setAllIngredients(ingredientOfMapped);
		opMega.setOperationApplied(this);
		opMega.setOriginal(modificationPoint.getCodeElement());
		opMega.setModificationPoint(modificationPoint);

		return opsMega;
	}

	protected List<Ingredient> computeIngredientsFromVarToReplace(ModificationPoint modificationPoint,
			CtVariableAccess variableAccessToReplace) {

		// TODO:cache of types -> ingredients.

		List<Ingredient> ingredients = new ArrayList<>();

		// All the methods in scope

		// All methods for a given target that
		CtTypeReference variableType = variableAccessToReplace.getType();

		CtClass classUnderAnalysis = modificationPoint.getCodeElement().getParent(CtClass.class);

		// List<CtVariable> varsInContext =
		// modificationPoint.getContextOfModificationPoint();

		// Ingredient ingredient = new Ingredient(iVarAccessFromContext);
		// // we use this property to indicate the old variable to replace
		// ingredient.setDerivedFrom(variableAccessToReplace);
		// ingredients.add(ingredient);

		List<CtInvocation> varM = SupportOperators.retrieveInvocationsFromVar(variableType, classUnderAnalysis,
				modificationPoint);

		for (CtInvocation invocationTomethod : varM) {
			Ingredient ingredient = new Ingredient(invocationTomethod);
			// // we use this property to indicate the old variable to replace
			ingredient.setDerivedFrom(variableAccessToReplace);
			ingredients.add(ingredient);
		}

		// List<CtInvocation> varOb =
		// SupportOperators.retrieveInvocationsFromVar(variableType,classUnderAnalysis,modificationPoint)

		return ingredients;
	}

	@Override
	public OperatorInstance getConcreteOperatorInstance(MetaOperatorInstance operatorInstance, int metaIdentifier) {

		Ingredient ingredient = operatorInstance.getAllIngredients().get(metaIdentifier);

		ModificationPoint modificationPoint = operatorInstance.getModificationPoint();

		CtExpression expressionSource = (CtExpression) ingredient.getDerivedFrom();
		CtExpression expressionTarget = (CtExpression) ingredient.getCode();

		MutationSupporter.clearPosition(expressionTarget);

		List<OperatorInstance> opsOfVariant = new ArrayList();

		OperatorInstance opInstace = new StatementOperatorInstance(modificationPoint, this, expressionSource,
				expressionTarget);
		opsOfVariant.add(opInstace);

		return opInstace;
	}

	@Override
	public boolean canBeAppliedToPoint(ModificationPoint point) {

		// See that the modification points are statements
		return (point.getCodeElement() instanceof CtStatement);
	}

}
