using Dumble.RecommendationService.Application.Contracts;
using Dumble.RecommendationService.Application.Features.Catalog;
using Dumble.SharedKernel.Events.Posts;
using MassTransit;

namespace Dumble.RecommendationService.Infrastructure.Messaging.Consumers;

/// <summary>Catalog sync: a new post becomes a Recombee item with its full profile.</summary>
public sealed class PostCreatedConsumer : IConsumer<PostCreatedEvent>
{
    private readonly IRecombeeClient _recombee;

    public PostCreatedConsumer(IRecombeeClient recombee) => _recombee = recombee;

    public Task Consume(ConsumeContext<PostCreatedEvent> context) =>
        _recombee.UpsertItemAsync(CatalogItemMapper.FromPostCreated(context.Message), context.CancellationToken);
}
