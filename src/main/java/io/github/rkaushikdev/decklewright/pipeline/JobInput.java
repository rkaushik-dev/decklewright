package io.github.rkaushikdev.decklewright.pipeline;

import io.github.rkaushikdev.decklewright.measure.Sampling;
import io.github.rkaushikdev.decklewright.profile.Profile;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * One capture of one accession, as a run is handed it.
 *
 * <p>The bytes are held as given, not copied, because a capture is a few hundred megabytes. Two
 * inputs are equal when every part is, the bytes compared by content.
 *
 * @param accession what the capture is of
 * @param bytes the capture as it came off the disk, in any format the codecs read
 * @param profile the resolved profile to run it under
 * @param sampling a frequency the caller measured, used over the one the reading carries
 * @param path where the bytes came from, which the codecs name when they cannot read them
 * @see Runner#runJob(JobInput, RunOptions)
 */
public record JobInput(
        String accession, byte[] bytes, Profile profile, Optional<Sampling> sampling, Optional<String> path) {

    /** An input of the given parts; the bytes are not copied. */
    public JobInput {
        Objects.requireNonNull(accession, "accession");
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(sampling, "sampling");
        Objects.requireNonNull(path, "path");
    }

    /** A capture with no measured frequency and nowhere it came from. */
    public static JobInput of(String accession, byte[] bytes, Profile profile) {
        return new JobInput(accession, bytes, profile, Optional.empty(), Optional.empty());
    }

    /** This input with a measured frequency. */
    public JobInput withSampling(Sampling measured) {
        return new JobInput(accession, bytes, profile, Optional.of(measured), path);
    }

    /** This input with other bytes, everything else the same. */
    public JobInput withBytes(byte[] other) {
        return new JobInput(accession, other, profile, sampling, path);
    }

    /** This input saying where its bytes came from. */
    public JobInput withPath(String from) {
        return new JobInput(accession, bytes, profile, sampling, Optional.of(from));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof JobInput that
                && accession.equals(that.accession)
                && Arrays.equals(bytes, that.bytes)
                && profile.equals(that.profile)
                && sampling.equals(that.sampling)
                && path.equals(that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accession, Arrays.hashCode(bytes), profile, sampling, path);
    }

    /** The accession, size and profile, such as {@code JobInput[g4181, 216058 bytes, house]}. */
    @Override
    public String toString() {
        return "JobInput[" + accession + ", " + bytes.length + " bytes, " + profile.name() + "]";
    }
}
