namespace Dumble.RecommendationService.Tests.TestDoubles;

/// <summary>A <see cref="TimeProvider"/> pinned to a fixed instant for deterministic tests.</summary>
internal sealed class FixedTimeProvider : TimeProvider
{
    private readonly DateTimeOffset _now;

    public FixedTimeProvider(DateTimeOffset now) => _now = now;

    public override DateTimeOffset GetUtcNow() => _now;
}
