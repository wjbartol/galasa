/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package dev.galasa.maven.plugin;
import static org.assertj.core.api.Assertions.*;


import org.junit.Test;


public class DeployTestCatalogTest {

    private class MockDeployTestCatalog extends DeployTestCatalog{

        public boolean getSkip(){
            return super.skip;
        }

        public boolean getTypoSkip() {
            return super.typoSkip;
        }

        public boolean getCorrectSkip() {
            return super.correctSkip;
        }

        private void setUp(boolean typo, boolean correct){
            setTypoSkip(this.typoSkip, typo);
            setCorrectSkip(this.correctSkip, correct);
        }

        private void setTypoSkip(boolean typoSkip, boolean typo) {
            super.typoSkip = typo;
        }
        private void setCorrectSkip(boolean correctSkip, boolean correct) {
            super.correctSkip = correct;
        }
    
    }

    @Test
    public void setSkipTrueWhenTypoSkipIsTrue() {
        //Given...
        boolean typoSkip = true;
        boolean correctSkip = false;
        MockDeployTestCatalog deployTest = new MockDeployTestCatalog();
        deployTest.setUp(typoSkip, correctSkip);
        
        //When...
        deployTest.setSkip();

        //Then...
        assertThat(deployTest.getTypoSkip()).isTrue();
        assertThat(deployTest.getCorrectSkip()).isFalse();
        assertThat(deployTest.getSkip()).isTrue();
    }

    @Test
    public void setSkipFalseWhenTypoSkipAndCorrectSkipIsFalse() {
        //Given...
        boolean typoSkip = false;
        boolean correctSkip = false;
        MockDeployTestCatalog deployTest = new MockDeployTestCatalog();
        deployTest.setUp(typoSkip, correctSkip);
        
        //When...
        deployTest.setSkip();

        //Then...
        assertThat(deployTest.getTypoSkip()).isFalse();
        assertThat(deployTest.getCorrectSkip()).isFalse();
        assertThat(deployTest.getSkip()).isFalse();
    }


    @Test
    public void setSkipTrueWhenCorrectSkipIsTrue() {
        //Given...
        boolean typoSkip = false;
        boolean correctSkip = true;
        MockDeployTestCatalog deployTest = new MockDeployTestCatalog();
        deployTest.setUp(typoSkip, correctSkip);
        
        //When...
        deployTest.setSkip();

        //Then...
        assertThat(deployTest.getTypoSkip()).isFalse();
        assertThat(deployTest.getCorrectSkip()).isTrue();
        assertThat(deployTest.getSkip()).isTrue();
    }

    @Test
    public void setSkipTrueWhenCorrectSkipAndTypoSkipIsTrue() {
        //Given...
        boolean typoSkip = true;
        boolean correctSkip = true;
        MockDeployTestCatalog deployTest = new MockDeployTestCatalog();
        deployTest.setUp(typoSkip, correctSkip);
        
        //When...
        deployTest.setSkip();

        //Then...
        assertThat(deployTest.getTypoSkip()).isTrue();
        assertThat(deployTest.getCorrectSkip()).isTrue();
        assertThat(deployTest.getSkip()).isTrue();
    }

}
