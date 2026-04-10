using MediatR;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Contracts.Hashtags;

namespace Dumble.PostService.Application.Features.Hashtags.Queries.GetTrending;

public class GetTrendingHashtagsQueryHandler : IRequestHandler<GetTrendingHashtagsQuery, List<HashtagResponse>>
{
    private readonly IHashtagRepository _hashtagRepository;

    public GetTrendingHashtagsQueryHandler(IHashtagRepository hashtagRepository)
    {
        _hashtagRepository = hashtagRepository;
    }

    public async Task<List<HashtagResponse>> Handle(GetTrendingHashtagsQuery request, CancellationToken ct)
    {
        var hashtags = await _hashtagRepository.GetTrendingAsync(request.Limit, ct);

        return hashtags.Select(h => new HashtagResponse(h.Id, h.Name, h.UsageCount)).ToList();
    }
}
