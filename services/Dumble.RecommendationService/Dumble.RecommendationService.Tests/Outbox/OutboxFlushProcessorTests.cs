using Dumble.RecommendationService.Application.Contracts;
using Dumble.RecommendationService.Application.Outbox;
using Dumble.RecommendationService.Domain.Outbox;
using Dumble.RecommendationService.Tests.TestDoubles;
using Microsoft.Extensions.Logging.Abstractions;
using Xunit;

namespace Dumble.RecommendationService.Tests.Outbox;

public class OutboxFlushProcessorTests
{
    private static readonly DateTimeOffset T = new(2026, 5, 30, 0, 0, 0, TimeSpan.Zero);

    private static OutboxInteraction AnInteraction()
        => OutboxInteraction.Create("u1", "p1", OutboxOperation.AddDetailView, T, T);

    private static OutboxFlushProcessor Processor(IOutboxFlushStore store, IRecombeeClient client)
        => new(store, client, NullLogger<OutboxFlushProcessor>.Instance);

    [Fact]
    public async Task Returns_zero_and_skips_recombee_when_nothing_pending()
    {
        var store = new FakeOutboxFlushStore();
        var client = new FakeRecombeeClient();

        var flushed = await Processor(store, client).FlushOnceAsync(100, CancellationToken.None);

        Assert.Equal(0, flushed);
        Assert.Equal(0, client.SendCalls);
    }

    [Fact]
    public async Task Sends_then_marks_sent_on_success()
    {
        var store = new FakeOutboxFlushStore();
        store.Seed(AnInteraction(), AnInteraction());
        var client = new FakeRecombeeClient();

        var flushed = await Processor(store, client).FlushOnceAsync(100, CancellationToken.None);

        Assert.Equal(2, flushed);
        Assert.Equal(1, client.SendCalls);
        Assert.Equal(2, store.Sent.Count);
        Assert.Empty(store.ReturnedToPending);
    }

    [Fact]
    public async Task Returns_batch_to_pending_and_rethrows_when_send_fails()
    {
        var store = new FakeOutboxFlushStore();
        store.Seed(AnInteraction());
        var client = new FakeRecombeeClient { ThrowOnSend = new InvalidOperationException("recombee down") };

        var processor = Processor(store, client);

        await Assert.ThrowsAsync<InvalidOperationException>(
            () => processor.FlushOnceAsync(100, CancellationToken.None));
        Assert.Empty(store.Sent);
        Assert.Single(store.ReturnedToPending);
    }

    [Fact]
    public async Task Claims_at_most_the_batch_size()
    {
        var store = new FakeOutboxFlushStore();
        store.Seed(AnInteraction(), AnInteraction(), AnInteraction());
        var client = new FakeRecombeeClient();

        var flushed = await Processor(store, client).FlushOnceAsync(2, CancellationToken.None);

        Assert.Equal(2, flushed);
        Assert.Equal(2, store.Sent.Count);
    }
}
