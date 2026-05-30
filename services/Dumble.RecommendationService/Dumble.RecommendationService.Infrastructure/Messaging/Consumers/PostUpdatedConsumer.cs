using Dumble.RecommendationService.Application.Contracts;
using Dumble.RecommendationService.Application.Features.Catalog;
using Dumble.SharedKernel.Events.Posts;
using MassTransit;

namespace Dumble.RecommendationService.Infrastructure.Messaging.Consumers;

/// <summary>Catalog sync: an edited post updates only the properties it changed (hashtags).</summary>
public sealed class PostUpdatedConsumer : IConsumer<PostUpdatedEvent>
{
    private readonly IRecombeeClient _recombee;

    public PostUpdatedConsumer(IRecombeeClient recombee) => _recombee = recombee;

    public Task Consume(ConsumeContext<PostUpdatedEvent> context) =>
        _recombee.UpsertItemAsync(CatalogItemMapper.FromPostUpdated(context.Message), context.CancellationToken);
}
