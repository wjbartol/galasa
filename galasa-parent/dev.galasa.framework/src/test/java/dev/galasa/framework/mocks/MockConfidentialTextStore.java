/*
 * Copyright contributors to the Galasa project
 */
package dev.galasa.framework.mocks;

import java.util.Map;

import dev.galasa.framework.spi.IConfidentialTextService;

public class MockConfidentialTextStore implements IConfidentialTextService {

    // private Map<String, String> confidentialTextProps;

    public MockConfidentialTextStore(Map<String, String> confidentialTextProps) {
        // this.confidentialTextProps = confidentialTextProps;
    }

    @Override
    public void registerText(String confidentialString, String comment) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'registerText'");
    }

    @Override
    public String removeConfidentialText(String text) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'removeConfidentialText'");
    }

    @Override
    public void shutdown() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'shutdown'");
    }

}
