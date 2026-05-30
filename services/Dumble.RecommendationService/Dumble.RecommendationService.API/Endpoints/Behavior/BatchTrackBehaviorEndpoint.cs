using Dumble.RecommendationService.Contracts.Behavior;
using FastEndpoints;
using MediatR;

namespace Dumble.RecommendationService.API.Endpoints.Behavior;

/// <summary>
/// Channel 1 (batched): a client reports several behaviour signals in one request — useful
/// for flushing buffered client-side signals on app background/foreground.
/// </summary>
public sealed class BatchTrackBehaviorEndpoint : Endpoint<BatchTrackBehaviorRequest>
{
    private readonly ISender _sender;
    private readonly TimeProvider _clock;

    public BatchTrackBehaviorEndpoint(ISender sender, TimeProvider clock)
    {
        _sender = sender;
        _clock = clock;
    }

    public override void Configure()
    {
        Post("/api/feed/behavior/batch");
        Claims("userId");
    }

    public override async Task HandleAsync(BatchTrackBehaviorRequest req, CancellationToken ct)
    {
        var userId = User.FindFirst("userId")!.Value;
        var now = _clock.GetUtcNow();
        foreach (var signal in req.Events)
            await _sender.Send(BehaviorCommandFactory.FromClient(userId, signal, now), ct);

        await SendNoContentAsync(ct);
    }
}
