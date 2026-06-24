using MediatR;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Contracts.Posts;

namespace Dumble.PostService.Application.Features.Posts.Queries.GetUserPostCount;

public class GetUserPostCountQueryHandler : IRequestHandler<GetUserPostCountQuery, PostCountResponse>
{
    private readonly IPostRepository _postRepository;

    public GetUserPostCountQueryHandler(IPostRepository postRepository)
    {
        _postRepository = postRepository;
    }

    public async Task<PostCountResponse> Handle(GetUserPostCountQuery request, CancellationToken ct)
    {
        var count = await _postRepository.CountByAuthorIdAsync(request.UserId, ct);
        return new PostCountResponse(count);
    }
}
