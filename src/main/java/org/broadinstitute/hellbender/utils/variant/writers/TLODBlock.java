package org.broadinstitute.hellbender.utils.variant.writers;

import htsjdk.samtools.util.Locatable;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.GenotypeBuilder;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.vcf.VCFConstants;
import org.broadinstitute.hellbender.utils.MathUtils;
import org.broadinstitute.hellbender.utils.Utils;
import org.broadinstitute.hellbender.utils.variant.GATKVCFConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Helper class for calculating a somatic LOD band in the SomaticGVCF writer
 *
 * A band contains LOD and DP values for a contiguous stretch of hom-ref genotypes,
 * and provides summary information about the entire block of genotypes.
 *
 * Genotypes within the TLODBlock are restricted to hom-ref genotypes within a band of LOD scores
 */
final class TLODBlock extends GVCFBlock {

    private final int minLOD, maxLOD;  //ints make inheritance easier and make sense for mitochondria; may need better precision for tumors

    private double maxBlockLOD = Double.NEGATIVE_INFINITY;
    //given that we're using the same LOD calculation as for
    // variants, more confident reference sites will have lower LOD so the most conservative thing is to take the max LOD of the block

    /**
     * Create a new HomRefBlock
     *
     * @param startingVC the VariantContext that starts this band (for starting position information)
     * @param lowerLODBound the lowerLODBound (inclusive) to use in this band
     * @param upperLODBound the upperLODBound (exclusive) to use in this band
     */
    public TLODBlock(final VariantContext startingVC, final int lowerLODBound, final int upperLODBound) {
        super(startingVC, Math.round(lowerLODBound), Math.round(upperLODBound));
        if ( lowerLODBound > upperLODBound ) { throw new IllegalArgumentException("bad lowerLODBound " + lowerLODBound + " as it's >= upperLODBound " + upperLODBound); }
        minLOD = lowerLODBound;
        maxLOD = upperLODBound;
    }

    // create a single Genotype with GQ and DP annotations
    @Override
    Genotype createHomRefGenotype(String sampleName) {
        final GenotypeBuilder gb = new GenotypeBuilder(sampleName, Collections.nCopies(2, getRef()));  //FIXME: for somatic stuff we output the genotype as diploid because that's familiar for human
        gb.noAD().noPL().noAttributes(); // clear all attributes

        gb.attribute(GATKVCFConstants.TUMOR_LOD_KEY, maxBlockLOD);
        gb.DP(getMedianDP());
        gb.attribute(GATKVCFConstants.MIN_DP_FORMAT_KEY, getMinDP());

        return gb.make();
    }

    /**
     * Add information from this Genotype to this band.
     *
     * @param pos Current genomic position. Must be 1 base after the previous position
     * @param genotype A non-null Genotype with GQ and DP attributes
     */
    @Override
    public void add(final int pos, final int newEnd, final Genotype genotype) {
        Utils.nonNull(genotype, "genotype cannot be null");
        if ( pos != end + 1 ) { throw new IllegalArgumentException("adding genotype at pos " + pos + " isn't contiguous with previous end " + end); }
        // Make sure the LOD is within the bounds of this band
        double currentLOD = Double.parseDouble(genotype.getExtendedAttribute(GATKVCFConstants.TUMOR_LOD_KEY).toString());
        if ( !withinBounds(currentLOD)) {
            throw new IllegalArgumentException("cannot add a genotype with LOD=" + genotype.getGQ() + " because it's not within bounds ["
                    + this.getLODLowerBound() + ',' + this.getLODUpperBound() + ')');
        }

        if( maxBlockLOD == Double.NEGATIVE_INFINITY || currentLOD > maxBlockLOD) {
            maxBlockLOD = currentLOD;
        }

        end = newEnd;
        DPs.add(Math.max(genotype.getDP(), 0)); // DP must be >= 0
    }

    /**
     * Is the LOD value within the bounds of this LOD (LOD >= minLOD && LOD < maxLOD)
     * @param LOD the LOD value to test
     * @return true if within bounds, false otherwise
     */
    public boolean withinBounds(final double LOD) {
        return LOD >= minLOD && LOD < maxLOD;
    }

    public double getMaxBlockLOD() {
        return maxBlockLOD;
    }

    double getLODUpperBound() {
        return maxLOD;
    }
    double getLODLowerBound() {
        return minLOD;
    }

    @Override
    public String toString() {
        return "HomRefBlock{" +
                "minLOD=" + minLOD +
                ", maxLOD=" + maxLOD +
                '}';
    }
}

