package io.github.rkaushikdev.decklewright;

import java.util.List;

/**
 * decklewright, the crop and derivative production library for the oversize reformatting lab, and
 * the list of what it promises to keep.
 *
 * <p>The surface is deliberately narrow. Nine tenths of the public types in the tree are public so
 * the layers can use one another, and are not worth promising to keep; a shop that reaches past
 * this list is welcome to but is on its own when a name moves. What is listed is what a caller needs
 * to take a capture and end up with a delivery, plus the errors it will have to catch.
 *
 * <p>Each name is relative to this package: a public type, such as {@code geom.Rect}, or a public
 * static member of one, such as {@code pipeline.WorkspaceRunner.processIncoming}, which stands for
 * every overload of it.
 */
public final class Decklewright {

    /** Version of the public surface, bumped by hand when something breaks. */
    public static final int API_VERSION = 2;

    /**
     * Every name this library promises, in one list, sorted.
     *
     * <p>It is here so that a test can hold the surface still. A public API that grows a name by
     * accident is a public API somebody depends on by accident, and the version number above stops
     * meaning anything the first time that happens without being noticed.
     */
    public static final List<String> PUBLIC_NAMES = List.of(
            "BadArgumentError",
            "CodecError",
            "DeckleError",
            "DeckleError.isDeckleError",
            "DeckleError.wrap",
            "Decklewright.API_VERSION",
            "Decklewright.PUBLIC_NAMES",
            "DetectionError",
            "ErrorCode",
            "GeometryError",
            "ManifestError",
            "PipelineError",
            "PlanError",
            "ProfileError",
            "QcError",
            "StoreError",
            "UnsupportedError",
            "cli.Main.run",
            "codec.Codecs.formatOf",
            "codec.Codecs.probeImage",
            "codec.Codecs.readImage",
            "codec.Codecs.writeImage",
            "codec.Inspections.describe",
            "codec.Inspections.inspect",
            "derive.Derivatives.deriveFromPlan",
            "derive.Derivatives.preservationCopy",
            "derive.FileNames.nameFor",
            "derive.FileNames.namesFor",
            "derive.Recipes.frequencyOf",
            "derive.Recipes.textOf",
            "detect.Captures.isUsable",
            "detect.Captures.readCapture",
            "detect.Captures.summarise",
            "geom.Angle",
            "geom.Insets",
            "geom.Point",
            "geom.Rect",
            "geom.Size",
            "manifest.Checksums.digest",
            "manifest.Checksums.digests",
            "manifest.Checksums.fixityOf",
            "manifest.Checksums.verifyAgainst",
            "manifest.Deliveries.deliveryFor",
            "manifest.Payloads.describePayload",
            "manifest.Payloads.objectionsToPayload",
            "manifest.Sidecars.parseSidecar",
            "manifest.Sidecars.writeSidecar",
            "measure.Length",
            "measure.Sampling",
            "measure.Tolerance",
            "pipeline.Policies.decide",
            "pipeline.Policies.policy",
            "pipeline.Policies.policyFor",
            "pipeline.Runner.describeRun",
            "pipeline.Runner.runBatch",
            "pipeline.Runner.runJob",
            "pipeline.Stages.stage",
            "pipeline.StandardStages.standardStages",
            "pipeline.WorkspaceRunner.process",
            "pipeline.WorkspaceRunner.processIncoming",
            "plan.CropPlan",
            "plan.CropPlanner.planCrop",
            "plan.Explanations.explain",
            "plan.Explanations.summarise",
            "profile.BuiltIn.BUILT_IN",
            "profile.BuiltIn.builtInNamed",
            "profile.BuiltIn.builtInNames",
            "profile.Diagnostics.describeProfile",
            "profile.ProfileLoader.loadAll",
            "profile.ProfileLoader.loadProfile",
            "profile.ProfileRegistry",
            "profile.ProfileRegistry.registryWith",
            "profile.Settings.settingsFor",
            "qc.Grading.gradeCapture",
            "qc.Grading.gradingToJson",
            "qc.Grading.reportOn",
            "qc.QcRunner.runQc",
            "qc.QcRunner.standardChecks",
            "raster.Surface",
            "raster.SurfaceView",
            "store.Dispatch.deliver",
            "store.Dispatch.recordFailure",
            "store.Dispatch.shiftReport",
            "store.Workspace");

    private Decklewright() {}
}
