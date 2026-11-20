package io.github.rkaushikdev.decklewright.pipeline;

import io.github.rkaushikdev.decklewright.PipelineError;
import io.github.rkaushikdev.decklewright.codec.Codecs;
import io.github.rkaushikdev.decklewright.derive.Derivatives;
import io.github.rkaushikdev.decklewright.derive.DeriveOptions;
import io.github.rkaushikdev.decklewright.derive.DerivedFile;
import io.github.rkaushikdev.decklewright.derive.DerivedSet;
import io.github.rkaushikdev.decklewright.detect.CaptureReading;
import io.github.rkaushikdev.decklewright.detect.Captures;
import io.github.rkaushikdev.decklewright.manifest.Deliveries;
import io.github.rkaushikdev.decklewright.manifest.DeliveryOptions;
import io.github.rkaushikdev.decklewright.manifest.FinishedRun;
import io.github.rkaushikdev.decklewright.plan.CropPlan;
import io.github.rkaushikdev.decklewright.plan.CropPlanner;
import io.github.rkaushikdev.decklewright.plan.PlannedCrop;
import io.github.rkaushikdev.decklewright.profile.Settings;
import io.github.rkaushikdev.decklewright.qc.QcInput;
import io.github.rkaushikdev.decklewright.qc.QcResult;
import io.github.rkaushikdev.decklewright.qc.QcRunner;
import io.github.rkaushikdev.decklewright.raster.Surface;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The standard run, stage by stage.
 *
 * <p>Each stage is a thin wrapper round a call into one of the layers below. That is on purpose: a
 * stage is where the wiring lives and nowhere else, so the detection layer never learns what a job is
 * and the pipeline never learns how a quad is fitted.
 *
 * <p>The values the stages pass along are {@code bytes} and {@code path} (supplied by the run),
 * {@code surface} (a {@link Surface}), {@code reading} (a {@link CaptureReading}), {@code plan} (a
 * {@link CropPlan}), {@code quality} (a {@link QcResult}), {@code files} (a list of {@link
 * DerivedFile}s, with {@code derivedBytes} and {@code derivedSteps} beside it) and {@code payload}
 * (a {@link io.github.rkaushikdev.decklewright.manifest.Payload}).
 */
public final class StandardStages {

    /** Read the bytes that came off the disk into pixels. */
    public static final Stage READ = Stages.stage("read", List.of("bytes"), List.of("surface"), StandardStages::read);

    /** Find the sheet, the target, the neatline and the bar. */
    public static final Stage DETECT =
            Stages.stage("detect", List.of("surface"), List.of("reading"), StandardStages::detect);

    /** Work out where to cut. */
    public static final Stage PLAN =
            Stages.stage("plan", List.of("surface", "reading"), List.of("plan"), StandardStages::plan);

    /**
     * Grade it, inside the planned crop when there is one. A lab that only cuts sheets does not grade,
     * and leaves this stage out.
     */
    public static final Stage GRADE =
            Stages.stage("grade", List.of("surface", "reading"), List.of("quality"), StandardStages::grade);

    /** Render, encode and name. */
    public static final Stage DERIVE =
            Stages.stage("derive", List.of("surface", "plan"), List.of("files"), StandardStages::derive);

    /** Build the delivery and check it against itself. */
    public static final Stage PACK = Stages.stage(
            "pack", List.of("surface", "reading", "plan", "files"), List.of("payload"), StandardStages::pack);

    private static final List<Stage> STANDARD = List.of(READ, DETECT, PLAN, GRADE, DERIVE, PACK);

    private StandardStages() {}

    /** The run a lab gets when it says nothing: read, detect, plan, grade, derive and pack. */
    public static List<Stage> standardStages() {
        return STANDARD;
    }

    private static Map<String, ?> read(Job job, StageContext context) {
        byte[] bytes = job.need("bytes", byte[].class);
        Surface surface = job.get("path", String.class)
                .map(path -> Codecs.readImage(bytes, path))
                .orElseGet(() -> Codecs.readImage(bytes));
        return Map.of("surface", surface);
    }

    private static Map<String, ?> detect(Job job, StageContext context) {
        return Map.of("reading", Captures.readCapture(job.need("surface", Surface.class), context.capture()));
    }

    private static Map<String, ?> plan(Job job, StageContext context) {
        PlannedCrop out = CropPlanner.planCrop(
                job.need("reading", CaptureReading.class),
                context.profile(),
                job.need("surface", Surface.class).bounds(),
                context.sampling(),
                context.crop());
        if (!out.settled()) {
            throw new PipelineError("the crop rules did not settle", Map.of("passes", out.passes()));
        }
        return Map.of("plan", out.plan());
    }

    private static Map<String, ?> grade(Job job, StageContext context) {
        QcInput input = new QcInput(
                job.need("surface", Surface.class),
                job.need("reading", CaptureReading.class),
                context.profile(),
                context.sampling(),
                job.get("plan", CropPlan.class).map(CropPlan::rect));
        return Map.of("quality", QcRunner.runQc(input));
    }

    private static Map<String, ?> derive(Job job, StageContext context) {
        DerivedSet out = Derivatives.deriveFromPlan(
                job.need("surface", Surface.class),
                job.need("plan", CropPlan.class),
                context.profile(),
                job.accession(),
                Settings.derivativeRecipes(context.profile()),
                new DeriveOptions(context.level(), DeriveOptions.defaults().fill()));
        Map<String, Object> produced = new LinkedHashMap<>();
        produced.put("files", out.files());
        produced.put("derivedBytes", out.bytes());
        produced.put("derivedSteps", out.steps());
        return produced;
    }

    private static Map<String, ?> pack(Job job, StageContext context) {
        FinishedRun run = new FinishedRun(
                job.accession(),
                context.profile(),
                job.need("surface", Surface.class),
                job.need("reading", CaptureReading.class),
                job.need("plan", CropPlan.class),
                filesOf(job),
                job.get("quality", QcResult.class),
                job.warnings());
        DeliveryOptions recorded = new DeliveryOptions(context.version(), context.host(), Optional.of(context.at()));
        return Map.of("payload", Deliveries.deliveryFor(run, recorded));
    }

    /**
     * The derivatives a job carries, each checked to be one.
     *
     * @throws PipelineError when the job does not carry them
     * @throws ClassCastException when it carries something else under their name
     */
    private static List<DerivedFile> filesOf(Job job) {
        return ((List<?>) job.need("files"))
                .stream().map(DerivedFile.class::cast).toList();
    }
}
