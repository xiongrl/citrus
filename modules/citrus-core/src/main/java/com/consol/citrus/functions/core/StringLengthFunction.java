package com.consol.citrus.functions.core;

import java.util.List;

import com.consol.citrus.exceptions.InvalidFunctionUsageException;
import com.consol.citrus.exceptions.TestSuiteException;
import com.consol.citrus.functions.Function;

public class StringLengthFunction implements Function {

    public String execute(List parameterList) throws TestSuiteException {
        if (parameterList == null || parameterList.isEmpty()) {
            throw new InvalidFunctionUsageException("Function parameters must not be empty");
        }

        return Integer.valueOf(((String)parameterList.get(0)).length()).toString();
    }

}