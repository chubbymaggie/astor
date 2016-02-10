package fr.inria.astor.test.repair.evaluation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.factory.Factory;
import spoon.reflect.factory.FactoryImpl;
import spoon.support.DefaultCoreFactory;
import spoon.support.StandardEnvironment;
import fr.inria.astor.core.entities.Gen;
import fr.inria.astor.core.entities.GenOperationInstance;
import fr.inria.astor.core.entities.GenSuspicious;
import fr.inria.astor.core.entities.ProgramVariant;
import fr.inria.astor.core.entities.taxonomy.GenProgMutationOperation;
import fr.inria.astor.core.loop.evolutionary.JGenProg;
import fr.inria.astor.core.util.ProcessUtil;
import fr.inria.main.ExecutionMode;
import fr.inria.main.evolution.AstorMain;
/**
 * This test cases aims at validating the mechanism of patch validation 
 * (We manually generate the candidate patches)
 * @author Matias Martinez
 *
 */
public class PatchValidationTest {

	protected Logger log = Logger.getLogger(PatchValidationTest.class.getName());
	
	@Test
	@Ignore
	public void testPatchMath0C1() throws Exception {
		
		log.debug("\nInit test with one failing TC");
		
		String dependenciespath = new File("./examples/Math-0c1ef/lib/junit-4.11.jar").getAbsolutePath() 
				+ File.pathSeparator
				+ new File("./examples/Math-0c1ef/lib/hamcrest-core-1.3.jar").getAbsolutePath();
		String projectId = "Math-0c1ef";
		String failing = "org.apache.commons.math3.primes.PrimesTest";
		File exampleLocation = new File("./examples/Math-0c1ef/");
		String location = exampleLocation.getAbsolutePath();
		String packageToInstrument = "org.apache.commons";
		double thfl = 0.5;
		
		String[] command = new String[]{"-dependencies",dependenciespath,"-location",location,
				"-flthreshold",Double.toString(thfl),
				"-package",packageToInstrument,
				"-failing", failing,
				"-id",projectId,
				"-population","1"};
		

		int processBeforeAll = ProcessUtil.currentNumberProcess();

		AstorMain main = new AstorMain();
		
		boolean correctArguments = main.processArguments(command);
		assertTrue(correctArguments);
		
		main.initProject(location, projectId, dependenciespath, packageToInstrument, thfl, failing);

		JGenProg jgp = main.createEngine(ExecutionMode.JGenProg);
		
		jgp.createInitialPopulation();
		
		Assert.assertEquals(1, jgp.getVariants().size());

		ProgramVariant variant = jgp.getVariants().get(0);
	
		int currentGeneration = 1;
		GenOperationInstance operation1 = createDummyOperation1(variant, currentGeneration);
		System.out.println("operation " + operation1);
		assertNotNull(operation1);

		boolean isSolution = false;
		isSolution = jgp.processCreatedVariant(variant, currentGeneration);
		// The model has not been changed.
		assertFalse("Any solution was expected here", isSolution);

		int afterFirstValidation = ProcessUtil.currentNumberProcess();
		
		jgp.applyNewOperationsToVariantModel(variant, currentGeneration);

		
		isSolution = jgp.processCreatedVariant(variant, currentGeneration);

		int afterPatchValidation = ProcessUtil.currentNumberProcess();

		assertTrue("The variant must be a solution",isSolution);

		System.out.println("\nSolutions:\n" + jgp.getSolutionData(jgp.getVariants(), 1));

		jgp.prepareNextGeneration(jgp.getVariants(), 1);

		assertNotNull("Any solution found",jgp.getSolutions());

		assertFalse("Solution set must be not empty",jgp.getSolutions().isEmpty());

		assertEquals("Problems with number of process", processBeforeAll, afterFirstValidation);

		assertEquals("Problems with number of  process", processBeforeAll, afterPatchValidation);

	}

	@Test
	@Ignore
	public void testPatchMath0C1TwoFailing() throws Exception {
		log.debug("\nInit test with two failing TC");
		String dependenciespath = new File("./examples/Math-0c1ef/lib/junit-4.11.jar").getAbsolutePath() 
				+ File.pathSeparator
				+ new File("./examples/Math-0c1ef/lib/hamcrest-core-1.3.jar").getAbsolutePath();
		String folder = "Math-0c1ef";
		// Only the first one fails
		String failing = "org.apache.commons.math3.primes.PrimesTest" + File.pathSeparator
				+ "org.apache.commons.math3.random.BitsStreamGeneratorTest";

		File projectId = new File("./examples/Math-0c1ef/");
		String location = projectId.getAbsolutePath();
		String packageToInstrument = "org.apache.commons";
		double thfl = 0.5;
		
		String[] command = new String[]{"-dependencies",dependenciespath,"-location",location,
				"-flthreshold",Double.toString(thfl),
				"-package",packageToInstrument,
				"-failing", failing,
				"-id",projectId.getName(),
				"-population","1"};
		

		AstorMain main = new AstorMain();
		
		boolean correctArguments = main.processArguments(command);
		assertTrue("Problems with arguments",correctArguments);
		
		main.initProject(location, folder, dependenciespath, packageToInstrument, thfl, failing);

		JGenProg jgp = main.createEngine(ExecutionMode.JGenProg);
		jgp.createInitialPopulation();
		
		Assert.assertEquals(1, jgp.getVariants().size());

		ProgramVariant variant = jgp.getVariants().get(0);
		
		int currentGeneration = 1;
		GenOperationInstance operation1 = createDummyOperation1(variant, currentGeneration);
		assertNotNull(operation1);

		boolean isSolution = false;
	
		jgp.applyNewOperationsToVariantModel(variant, currentGeneration);
		
		isSolution = jgp.processCreatedVariant(variant, currentGeneration);
		assertTrue("A solution is attended",isSolution);

	}
	private GenOperationInstance createDummyOperation1(ProgramVariant variant, int currentGeneration) {

		GenSuspicious genSusp = searchSuspiciousElement(variant, "n += 3", " ", 93);
		assertNotNull(genSusp);

		CtElement targetStmt = genSusp.getCodeElement();
		CtElement fix = createFix1();
		assertEquals(fix.toString(), "n += 2");

		GenOperationInstance operation = new GenOperationInstance();

		operation.setOperationApplied(GenProgMutationOperation.REPLACE);
		operation.setGen(genSusp);
		operation.setParentBlock((CtBlock) targetStmt.getParent());
		operation.setOriginal(targetStmt);
		operation.setModified(fix);

		variant.putGenOperation(currentGeneration, operation);
		operation.setGen(genSusp);

		return operation;
	}

	@Test
	public void testCreateFix1() {
		assertEquals(createFix1().toString(), "n += 2");
	}

	public CtElement createFix1() {
		CtElement gen = createPatchStatementCode("int n=0; n += 2");
		CtElement fix = ((CtBlock) gen.getParent()).getStatement(1);
		return fix;
	}
	

	public CtStatement createPatchStatementCode(String snippet) {

		Factory factory = new FactoryImpl(new DefaultCoreFactory(), new StandardEnvironment());
		CtStatement st = (factory).Code().createCodeSnippetStatement(snippet).compile();
		return st;
	}

	public GenSuspicious searchSuspiciousElement(ProgramVariant variant, String snippet, String fileName, int line) {

		for (Gen gen : variant.getGenList()) {

			if (gen.getCodeElement().toString().equals(snippet) && gen.getCodeElement().getPosition().getLine() == line)
				return (GenSuspicious) gen;
		}

		return null;
	}	
	
	
	
}
