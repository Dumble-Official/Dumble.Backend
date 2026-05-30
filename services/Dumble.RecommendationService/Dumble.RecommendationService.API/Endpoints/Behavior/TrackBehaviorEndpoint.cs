using Dumble.RecommendationService.Application.Features.Interactions;
using Dumble.RecommendationService.Application.Features.Interactions.RecordInteraction;
using Dumble.RecommendationService.Contracts.Behavior;
using Dumble.RecommendationService.Domain.Outbox;
using FastEndpoints;
using MediatR;

namespace Dumble.RecommendationService.API.Endpoints.Behavior;

/// <summary>
/// Channel 1: a client reports a single behaviour signal (view/click/dwell) for a post.
/// Relocated from SocialService at the same path so the app needs no change.
/// </summary>
public sealed class TrackBehaviorEndpoint : Endpoint<TrackBehaviorRequest>
{
    private readonly ISender _sender;
    private readonly TimeProvider _clock;

    public TrackBehaviorEndpoint(ISender sender, TimeProvider clock)
    {
        _sender = sender;
        _clock = clock;
    }

    public override void Configure()
    {
        Post("/api/feed/behavior");
        Claims("userId");
    }

    public override async Task HandleAsync(TrackBehaviorRequest req, CancellationToken ct)
    {
        var userId = User.FindFirst("userId")!.Value;
        await _sender.Send(BehaviorCommandFactory.FromClient(userId, req, _clock.GetUtcNow()), ct);
        await SendNoContentAsync(ct);
    }
}

/// <summary>Builds a <see cref="RecordInteractionCommand"/> from a client request + server clock.</summary>
internal static class BehaviorCommandFactory
{
    public static RecordInteractionCommand FromClient(string userId, TrackBehaviorRequest req, DateTimeOffset now)
    {
        var signal = ClientSignalMapper.Parse(req.EventType);
        int? duration = signal == InteractionSignal.Dwell && int.TryParse(req.EventData, out var seconds)
            ? seconds
            : null;
        return new RecordInteractionCommand(userId, req.PostId, signal, now, duration);
    }
}
