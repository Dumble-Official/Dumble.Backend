namespace Dumble.RecommendationService.Infrastructure.Recombee;

public sealed class RecombeeOptions
{
    public const string SectionName = "Recombee";

    /// <summary>Recombee database id. Secret-adjacent; supplied via env, never committed.</summary>
    public string DatabaseId { get; set; } = "";

    /// <summary>Server-side private token (D1.3 — never the public/client token). Supplied via env.</summary>
    public string PrivateToken { get; set; } = "";

    /// <summary>Recombee region the database lives in, e.g. "eu-west".</summary>
    public string Region { get; set; } = "";

    /// <summary>Max interactions drained and sent per flush cycle.</summary>
    public int FlushBatchSize { get; set; } = 100;

    /// <summary>Delay between flush cycles when the queue is not full.</summary>
    public int FlushIntervalSeconds { get; set; } = 5;

    /// <summary>Whether the periodic catalog reconcile worker runs (D17 drift-healing safety net).</summary>
    public bool ReconcileEnabled { get; set; } = true;

    /// <summary>Hours between catalog reconcile sweeps. Low frequency by design — this is a backstop.</summary>
    public int ReconcileIntervalHours { get; set; } = 24;

    /// <summary>
    /// Whether the reconcile also sweeps orphans (Recombee items whose source post is gone).
    /// Off by default — deletes are destructive, so this is opt-in.
    /// </summary>
    public bool OrphanSweepEnabled { get; set; } = false;

    /// <summary>
    /// When the orphan sweep runs, only report what it would delete instead of deleting. Defaults
    /// to true so an operator can watch the logs before enabling real deletes.
    /// </summary>
    public bool OrphanSweepDryRun { get; set; } = true;

    public bool IsConfigured =>
        !string.IsNullOrWhiteSpace(DatabaseId) && !string.IsNullOrWhiteSpace(PrivateToken);
}
