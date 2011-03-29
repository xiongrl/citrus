/*
 * Copyright 2006-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.consol.citrus.validation.text;

import org.springframework.integration.Message;
import org.springframework.integration.support.MessageBuilder;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.consol.citrus.exceptions.ValidationException;
import com.consol.citrus.testng.AbstractBaseTest;

/**
 * @author Christoph Deppisch
 */
public class PlainTextMessageValidatorTest extends AbstractBaseTest {

    @Test
    public void testPlainTextValidation() {
        PlainTextMessageValidator validator = new PlainTextMessageValidator();
        
        Message<String> receivedMessage = MessageBuilder.withPayload("Hello World!").build();
        Message<String> controlMessage = MessageBuilder.withPayload("Hello World!").build();
        
        validator.validateMessagePayload(receivedMessage, controlMessage, context);
    }
    
    @Test
    public void testPlainTextValidationVariableSupport() {
        PlainTextMessageValidator validator = new PlainTextMessageValidator();
        
        Message<String> receivedMessage = MessageBuilder.withPayload("Hello World!").build();
        Message<String> controlMessage = MessageBuilder.withPayload("Hello ${world}!").build();
        
        context.setVariable("world", "World");
        
        validator.validateMessagePayload(receivedMessage, controlMessage, context);
    }
    
    @Test
    public void testPlainTextValidationWrongValue() {
        PlainTextMessageValidator validator = new PlainTextMessageValidator();
        
        Message<String> receivedMessage = MessageBuilder.withPayload("Hello World!").build();
        Message<String> controlMessage = MessageBuilder.withPayload("Hello Citrus!").build();
        
        try {
            validator.validateMessagePayload(receivedMessage, controlMessage, context);
        } catch (ValidationException e) {
            Assert.assertTrue(e.getMessage().contains("expected 'Hello Citrus!'"));
            Assert.assertTrue(e.getMessage().contains("but was 'Hello World!'"));
            
            return;
        }
        
        Assert.fail("Missing validation exception due to wrong number of JSON entries");
    }
}
