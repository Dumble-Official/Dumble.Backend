using Dumble.RecommendationService.Application.Contracts;
using Dumble.SharedKernel.Events.Posts;
using MassTransit;

namespace Dumble.RecommendationService.Infrastructure.Messaging.Consumers;

/// <summary>Catalog sync: a deleted post is hard-deleted from Recombee so it can never be
/// recommended again (D11). PostService keeps the row (soft delete) for future model training.</summary>
public sealed class PostDeletedConsumer : IConsumer<PostDeletedEvent>
{
    private readonly IRecombeeClient _recombee;

    public PostDeletedConsumer(IRecombeeClient recombee) => _recombee = recombee;

    public Task Consume(ConsumeContext<PostDeletedEvent> context) =>
        _recombee.DeleteItemAsync(context.Message.PostId, context.CancellationToken);
}
