using Dumble.RecommendationService.Contracts.Common;
using Dumble.RecommendationService.Contracts.Suggestions;
using MediatR;

namespace Dumble.RecommendationService.Application.Features.Suggestions.GetSuggestedUsers;

/// <summary>Users to suggest the given user follow.</summary>
public sealed record GetSuggestedUsersQuery(string UserId, int Limit)
    : IRequest<CursorPagedResponse<SuggestedUserResponse>>;
