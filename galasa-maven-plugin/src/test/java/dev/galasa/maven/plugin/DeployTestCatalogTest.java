/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package dev.galasa.maven.plugin;
import static org.assertj.core.api.Assertions.*;


import org.junit.Test;


public class DeployTestCatalogTest {

    @Test
    public void setCorrectBooleanValueTrueWhenTypoSkipIsTrue() {
        //Given...
        boolean typoSkip = true;
        boolean correctSkip = false;
        
        //When...
        boolean skipResult = DeployTestCatalog.setCorrectBooleanValue(correctSkip, typoSkip);

        //Then...
        assertThat(skipResult).isTrue();
    }

    @Test
    public void setCorrectBooleanValueFalseWhenTypoSkipAndCorrectSkipIsFalse() {
        //Given...
        boolean typoSkip = false;
        boolean correctSkip = false;
        
        //When...
        boolean skipResult = DeployTestCatalog.setCorrectBooleanValue(correctSkip, typoSkip);

        //Then...
        assertThat(skipResult).isFalse();
    }


    @Test
    public void setCorrectBooleanValueTrueWhenCorrectSkipIsTrue() {
        //Given...
        boolean typoSkip = false;
        boolean correctSkip = true;
        
        //When...
        boolean skipResult = DeployTestCatalog.setCorrectBooleanValue(correctSkip, typoSkip);

        //Then...
        assertThat(skipResult).isTrue();
    }

    @Test
    public void setCorrectBooleanValueTrueWhenCorrectSkipAndTypoSkipIsTrue() {
        //Given...
        boolean typoSkip = true;
        boolean correctSkip = true;
        
        //When...
        boolean skipResult = DeployTestCatalog.setCorrectBooleanValue(correctSkip, typoSkip);

        //Then...
        assertThat(skipResult).isTrue();
    }

}
