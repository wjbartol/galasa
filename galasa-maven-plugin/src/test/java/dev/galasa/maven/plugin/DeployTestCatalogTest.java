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
    public void setSkipTrueWhenTypoSkipIsTrue() {
        //Given...
        boolean typoSkip = true;
        boolean correctSkip = false;
        
        //When...
        boolean skipResult = DeployTestCatalog.setSkip(correctSkip, typoSkip);

        //Then...
        assertThat(skipResult).isTrue();
    }

    @Test
    public void setSkipFalseWhenTypoSkipAndCorrectSkipIsFalse() {
        //Given...
        boolean typoSkip = false;
        boolean correctSkip = false;
        
        //When...
        boolean skipResult = DeployTestCatalog.setSkip(correctSkip, typoSkip);

        //Then...
        assertThat(skipResult).isFalse();
    }


    @Test
    public void setSkipTrueWhenCorrectSkipIsTrue() {
        //Given...
        boolean typoSkip = false;
        boolean correctSkip = true;
        
        //When...
        boolean skipResult = DeployTestCatalog.setSkip(correctSkip, typoSkip);

        //Then...
        assertThat(skipResult).isTrue();
    }

    @Test
    public void setSkipTrueWhenCorrectSkipAndTypoSkipIsTrue() {
        //Given...
        boolean typoSkip = true;
        boolean correctSkip = true;
        
        //When...
        boolean skipResult = DeployTestCatalog.setSkip(correctSkip, typoSkip);

        //Then...
        assertThat(skipResult).isTrue();
    }

}
