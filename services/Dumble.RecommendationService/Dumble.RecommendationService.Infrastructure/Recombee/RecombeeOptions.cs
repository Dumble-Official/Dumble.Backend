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

    public bool IsConfigured =>
        !string.IsNullOrWhiteSpace(DatabaseId) && !string.IsNullOrWhiteSpace(PrivateToken);
}
