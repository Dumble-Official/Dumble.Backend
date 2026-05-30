using Dumble.RecommendationService.Application.Features.Suggestions.GetSuggestedUsers;
using Dumble.RecommendationService.Contracts.Common;
using Dumble.RecommendationService.Contracts.Suggestions;
using FastEndpoints;
using MediatR;

namespace Dumble.RecommendationService.API.Endpoints.Feed;

/// <summary>Personalized "who to follow" suggestions for the calling user.</summary>
public sealed class GetSuggestedUsersEndpoint : EndpointWithoutRequest<CursorPagedResponse<SuggestedUserResponse>>
{
    private readonly ISender _sender;

    public GetSuggestedUsersEndpoint(ISender sender) => _sender = sender;

    public override void Configure()
    {
        Get("/api/feed/suggested-users");
        Claims("userId");
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var userId = User.FindFirst("userId")!.Value;
        var limit = Math.Clamp(Query<int?>("limit", isRequired: false) ?? 10, 1, 50);

        var result = await _sender.Send(new GetSuggestedUsersQuery(userId, limit), ct);
        await SendAsync(result, cancellation: ct);
    }
}
