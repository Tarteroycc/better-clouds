package com.qendolin.betterclouds.platform.neoforge;

import com.qendolin.betterclouds.platform.ModVersion;

//? if neoforge {
import org.apache.maven.artifact.versioning.ArtifactVersion;
public class ModVersionImpl extends ModVersion {

    private final ArtifactVersion delegate;

    public ModVersionImpl(ArtifactVersion delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean isPresent() {
        return true;
    }

    @Override
    public String getFriendlyString() {
        return delegate.getQualifier();
    }
}
//?} else {
/*public abstract class ModVersionImpl extends ModVersion {
}*/
//?}