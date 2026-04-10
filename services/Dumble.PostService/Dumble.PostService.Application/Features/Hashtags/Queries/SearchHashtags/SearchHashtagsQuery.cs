using MediatR;
using Dumble.PostService.Contracts.Hashtags;

namespace Dumble.PostService.Application.Features.Hashtags.Queries.SearchHashtags;

public record SearchHashtagsQuery(string Query, int Limit = 20) : IRequest<List<HashtagResponse>>;
