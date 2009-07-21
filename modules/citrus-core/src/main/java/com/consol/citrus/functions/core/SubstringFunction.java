package com.consol.citrus.functions.core;

import java.util.List;

import com.consol.citrus.exceptions.InvalidFunctionUsageException;
import com.consol.citrus.exceptions.TestSuiteException;
import com.consol.citrus.functions.Function;

public class SubstringFunction implements Function {

    public String execute(List parameterList) throws TestSuiteException {
        if (parameterList == null || parameterList.isEmpty()) {
            throw new InvalidFunctionUsageException("Function parameters must not be empty");
        }

        String resultString = (String)parameterList.get(0);

        String beginIndex = null;
        String endIndex = null;

        if (parameterList.size()>1) {
            beginIndex = (String)parameterList.get(1);
        }

        if (parameterList.size()>2) {
            endIndex = (String)parameterList.get(2);
        }

        if (endIndex != null && endIndex.length()>0) {
            resultString = resultString.substring(new Integer(beginIndex).intValue(), new Integer(endIndex).intValue());
        } else {
            resultString = resultString.substring(new Integer(beginIndex).intValue());
        }

        return resultString;
    }

}