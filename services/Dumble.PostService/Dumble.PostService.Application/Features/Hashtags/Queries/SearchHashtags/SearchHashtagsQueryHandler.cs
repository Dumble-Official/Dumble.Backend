using MediatR;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Contracts.Hashtags;

namespace Dumble.PostService.Application.Features.Hashtags.Queries.SearchHashtags;

public class SearchHashtagsQueryHandler : IRequestHandler<SearchHashtagsQuery, List<HashtagResponse>>
{
    private readonly IHashtagRepository _hashtagRepository;

    public SearchHashtagsQueryHandler(IHashtagRepository hashtagRepository)
    {
        _hashtagRepository = hashtagRepository;
    }

    public async Task<List<HashtagResponse>> Handle(SearchHashtagsQuery request, CancellationToken ct)
    {
        var query = request.Query.TrimStart('#').ToLowerInvariant().Trim();
        var hashtags = await _hashtagRepository.SearchAsync(query, request.Limit, ct);

        return hashtags.Select(h => new HashtagResponse(h.Id, h.Name, h.UsageCount)).ToList();
    }
}
