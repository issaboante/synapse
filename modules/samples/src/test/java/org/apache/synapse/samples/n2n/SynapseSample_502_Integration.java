package org.apache.synapse.samples.n2n;

import org.apache.synapse.Constants;
import samples.userguide.StockQuoteClient;

/**
 *
 */
public class SynapseSample_502_Integration extends AbstractAutomationTestCase {

    protected void setUp() throws Exception {
        System.setProperty(Constants.SYNAPSE_XML, SAMPLE_CONFIG_ROOT_PATH + "synapse_sample_502.xml");
        System.setProperty("addurl", "http://localhost:9000/soap/SimpleStockQuoteService");
        System.setProperty("trpurl", SYNAPSE_BASE_URL);
        super.setUp();
    }

    public void testSample() throws Exception {
        String resultString = getStringResultOfTest(StockQuoteClient.executeTestClient());
        assertXpathExists("ns:getQuoteResponse", resultString);
        assertXpathExists("ns:getQuoteResponse/ns:return", resultString);
    }
}
