using MediatR;
using Dumble.PostService.Application.Contracts;
using Dumble.PostService.Domain.Enums;
using Dumble.SharedKernel.Contracts;
using Dumble.SharedKernel.Enums;

namespace Dumble.PostService.Application.Features.Posts.Commands.SetPostFlag;

public class SetPostFlagCommandHandler : IRequestHandler<SetPostFlagCommand>
{
    private readonly IPostRepository _postRepository;
    private readonly ILoggedInUserService _userService;

    public SetPostFlagCommandHandler(IPostRepository postRepository, ILoggedInUserService userService)
    {
        _postRepository = postRepository;
        _userService = userService;
    }

    public async Task Handle(SetPostFlagCommand request, CancellationToken ct)
    {
        var currentUser = _userService.GetCurrentUser();
        if (!currentUser.IsInAnyRole(UserType.Admin, UserType.Moderator))
            throw new UnauthorizedAccessException("Only a moderator or admin can flag posts");

        var post = await _postRepository.GetByIdAsync(request.PostId, ct);
        if (post is null || post.Status == PostStatus.Deleted)
            throw new KeyNotFoundException($"Post {request.PostId} not found");

        // Flag/unflag toggles between Flagged and Active; a deleted post is never reachable here.
        post.Status = request.Flagged ? PostStatus.Flagged : PostStatus.Active;
        post.UpdatedAt = DateTime.UtcNow;
        await _postRepository.UpdateAsync(post, ct);
    }
}
