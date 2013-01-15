/*
 * =============================================================================
 * 
 *   Copyright (c) 2011-2012, The THYMELEAF team (http://www.thymeleaf.org)
 * 
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 * 
 * =============================================================================
 */
package org.thymeleaf.testing.templateengine.engine;

import java.io.StringWriter;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.IContext;
import org.thymeleaf.context.ProcessingContext;
import org.thymeleaf.dialect.IDialect;
import org.thymeleaf.fragment.IFragmentSpec;
import org.thymeleaf.testing.templateengine.context.ContextNaming;
import org.thymeleaf.testing.templateengine.engine.resolver.TestTemplateResolver;
import org.thymeleaf.testing.templateengine.test.ITest;
import org.thymeleaf.testing.templateengine.test.ITestIterator;
import org.thymeleaf.testing.templateengine.test.ITestReporter;
import org.thymeleaf.testing.templateengine.test.ITestResource;
import org.thymeleaf.testing.templateengine.test.ITestResult;
import org.thymeleaf.testing.templateengine.test.ITestSequence;
import org.thymeleaf.testing.templateengine.test.ITestSuite;
import org.thymeleaf.testing.templateengine.test.ITestable;
import org.thymeleaf.testing.templateengine.test.StringTestResource;
import org.thymeleaf.testing.templateengine.test.SuccessExpectedTest;
import org.thymeleaf.testing.templateengine.test.TestSuite;
import org.thymeleaf.util.Validate;





public class TestExecutor {
    
    
    
    public static void execute(final ITestSuite suite) {
        try {
            executeSuite(suite);
        } catch (final TestEngineExecutionException e) {
            throw e;
        } catch (final Exception e) {
            throw new TestEngineExecutionException(e);
        }
    }
    
 
    
    private static long executeSuite(final ITestSuite suite) {

        Validate.notNull(suite, "Suite cannot be null");
        if (suite.getName() == null) {
            throw new TestEngineExecutionException("Test suites must have a name (null returned)");
        }
        
        final ITestSequence sequence = suite.getSequence(); 
        final ITestReporter reporter = suite.getReporter();
        
        
        final TestTemplateResolver templateResolver = new TestTemplateResolver();
        final List<IDialect> dialects = suite.getDialects();
        
        final TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(templateResolver);
        templateEngine.setDialects(new HashSet<IDialect>(dialects));
        
        final TestExecutionContext testExecutionContext = new TestExecutionContext(suite); 
        
        reporter.suiteStart(suite);
        
        final long executionTimeNanos = 
                executeSequence(sequence, suite.getReporter(), templateEngine, testExecutionContext);
        
        reporter.suiteEnd(suite, executionTimeNanos);
        
        return executionTimeNanos;
        
    }


    
    private static long executeSequence(final ITestSequence sequence, final ITestReporter reporter, 
            final TemplateEngine templateEngine, final TestExecutionContext testExecutionContext) {

        reporter.sequenceStart(sequence);
        
        long totalTimeNanos = 0L;
        
        final List<ITestable> elements = sequence.getElements();
        
        for (final ITestable element : elements) {
            
            if (element instanceof ITestSequence) {
                final long elementExecTimeNanos = 
                        executeSequence((ITestSequence)element, reporter, templateEngine, testExecutionContext);
                totalTimeNanos += elementExecTimeNanos;
            } else if (element instanceof ITestIterator) {
                final long elementExecTimeNanos = 
                        executeIterator((ITestIterator)element, reporter, templateEngine, testExecutionContext);
                totalTimeNanos += elementExecTimeNanos;
            } else if (element instanceof ITest) {
                final long elementExecTimeNanos = 
                        executeTest((ITest)element, reporter, templateEngine, testExecutionContext);
                totalTimeNanos += elementExecTimeNanos;
            } else {
                // Should never happen
                throw new TestEngineExecutionException(
                        "ITestable implementation \"" + element.getClass() + "\" is not recognized");
            }
            
        }
        
        reporter.sequenceEnd(sequence, totalTimeNanos);
        
        return totalTimeNanos;
        
    }
    
    
    
    private static long executeIterator(final ITestIterator iterator, final ITestReporter reporter, 
            final TemplateEngine templateEngine, final TestExecutionContext testExecutionContext) {

        reporter.iteratorStart(iterator);
        
        final int iterations = iterator.getIterations();
        final ITestable element = iterator.getIteratedElement();
        
        long totalTimeNanos = 0L;
        
        for (int i = 0; i < iterations; i++) {
            
            if (element instanceof ITestSequence) {
                final long elementExecTimeNanos = 
                        executeSequence((ITestSequence)element, reporter, templateEngine, testExecutionContext);
                totalTimeNanos += elementExecTimeNanos;
            } else if (element instanceof ITestIterator) {
                final long elementExecTimeNanos = 
                        executeIterator((ITestIterator)element, reporter, templateEngine, testExecutionContext);
                totalTimeNanos += elementExecTimeNanos;
            } else if (element instanceof ITest) {
                final long elementExecTimeNanos = 
                        executeTest((ITest)element, reporter, templateEngine, testExecutionContext);
                totalTimeNanos += elementExecTimeNanos;
            } else {
                // Should never happen
                throw new TestEngineExecutionException(
                        "ITestable implementation \"" + element.getClass() + "\" is not recognized");
            }
            
        }
        
        reporter.iteratorEnd(iterator, totalTimeNanos);
        
        return totalTimeNanos;
        
    }
    
    
    
    private static long executeTest(final ITest test, final ITestReporter reporter, 
            final TemplateEngine templateEngine, final TestExecutionContext testExecutionContext) {

        
        final String testName = testExecutionContext.registerTest(test);
        
        reporter.testStart(test, testName);
        
        final IContext context = test.getContext();
        
        final Map<String,Object> localVariables = 
                Collections.singletonMap(
                        ContextNaming.TEST_EXECUTION_CONTEXT_VARIABLE_NAME, (Object)testExecutionContext);
        
        final ProcessingContext processingContext = new ProcessingContext(context, localVariables);
        
        final IFragmentSpec fragmentSpec = test.getFragmentSpec();

        final StringWriter writer = new StringWriter();

        ITestResult testResult = null;
        
        long startTimeNanos = System.nanoTime();
        long endTimeNanos;
        try {
            
            templateEngine.process(testName, processingContext, fragmentSpec, writer);
            endTimeNanos = System.nanoTime();
            
            final String result = writer.toString();
            testResult = test.evalResult(testName, result);
            
        } catch (final Throwable t) {
            endTimeNanos = System.nanoTime();
            testResult = test.evalResult(testName, t);
        }
        
        final long totalTimeNanos = (endTimeNanos - startTimeNanos);
        
        reporter.testEnd(test, testName, totalTimeNanos, testResult);
        
        return totalTimeNanos;
        
    }
    
  
    
    
    private TestExecutor() {
        super();
    }
    
    
    
    
    
    
    
    public static void main(final String[] args) {
        
        try {
            
            final ITestResource res0 = new StringTestResource("hello!");
            final ITestResource res1 = new StringTestResource("goodbye!");
            final ITestResource res2 = new StringTestResource("<span th:text=\"${'hey!'}\">cucu</span>");
            final ITestResource res3 = new StringTestResource("<span>hey!</span>");
            
            final ITest test0 = new SuccessExpectedTest(res1, true, res0);
            final ITest test1 = new SuccessExpectedTest(res2, false, res0);
            final ITest test2 = new SuccessExpectedTest(res2, true, res3);
            
            final ITestSuite testSuite = new TestSuite("testing01", test0, test2, test1, test2);

            
            execute(testSuite);
            
            
            
        } catch (final Throwable t) {
            t.printStackTrace();
        }
        
        
    }
    
    
    
    
    
}