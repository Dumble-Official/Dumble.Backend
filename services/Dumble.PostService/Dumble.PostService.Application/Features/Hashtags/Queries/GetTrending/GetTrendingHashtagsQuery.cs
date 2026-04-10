using MediatR;
using Dumble.PostService.Contracts.Hashtags;

namespace Dumble.PostService.Application.Features.Hashtags.Queries.GetTrending;

public record GetTrendingHashtagsQuery(int Limit = 20) : IRequest<List<HashtagResponse>>;
