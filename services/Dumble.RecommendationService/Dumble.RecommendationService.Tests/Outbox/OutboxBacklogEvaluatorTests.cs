using Dumble.RecommendationService.Application.Contracts;
using Dumble.RecommendationService.Application.Outbox;
using Xunit;

namespace Dumble.RecommendationService.Tests.Outbox;

public class OutboxBacklogEvaluatorTests
{
    private static readonly DateTimeOffset Now = new(2026, 5, 30, 12, 0, 0, TimeSpan.Zero);
    private static readonly TimeSpan MaxAge = TimeSpan.FromMinutes(5);

    [Fact]
    public void Empty_backlog_is_healthy()
    {
        var result = OutboxBacklogEvaluator.Evaluate(new OutboxBacklog(0, null), Now, MaxAge);
        Assert.Equal(OutboxHealth.Healthy, result.Health);
    }

    [Fact]
    public void Recent_pending_is_healthy()
    {
        var backlog = new OutboxBacklog(3, Now.AddMinutes(-1));
        var result = OutboxBacklogEvaluator.Evaluate(backlog, Now, MaxAge);
        Assert.Equal(OutboxHealth.Healthy, result.Health);
    }

    [Fact]
    public void Stale_pending_beyond_threshold_is_degraded()
    {
        var backlog = new OutboxBacklog(42, Now.AddMinutes(-10));
        var result = OutboxBacklogEvaluator.Evaluate(backlog, Now, MaxAge);
        Assert.Equal(OutboxHealth.Degraded, result.Health);
        Assert.Contains("stalled", result.Description);
    }

    [Fact]
    public void Pending_exactly_at_threshold_is_degraded()
    {
        var backlog = new OutboxBacklog(1, Now - MaxAge);
        var result = OutboxBacklogEvaluator.Evaluate(backlog, Now, MaxAge);
        Assert.Equal(OutboxHealth.Degraded, result.Health);
    }
}
