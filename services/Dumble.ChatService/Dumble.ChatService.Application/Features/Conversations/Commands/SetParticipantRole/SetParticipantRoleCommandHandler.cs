using Dumble.ChatService.Application.Contracts;
using MediatR;

namespace Dumble.ChatService.Application.Features.Conversations.Commands.SetParticipantRole;

public class SetParticipantRoleCommandHandler(
    IConversationRepository conversationRepository
) : IRequestHandler<SetParticipantRoleCommand>
{
    private static readonly string[] ValidRoles = { "Admin", "Member" };

    public async Task Handle(SetParticipantRoleCommand request, CancellationToken cancellationToken)
    {
        if (!ValidRoles.Contains(request.Role))
            throw new ArgumentException($"Role must be one of: {string.Join(", ", ValidRoles)}");

        var conversation = await conversationRepository.GetByIdAsync(request.ConversationId, cancellationToken)
            ?? throw new KeyNotFoundException($"Conversation '{request.ConversationId}' not found.");

        var caller = conversation.Participants.FirstOrDefault(p => p.UserId == request.CallerId);
        if (caller is null || caller.Role != "Admin")
            throw new UnauthorizedAccessException("Only an admin can change participant roles");

        var target = conversation.Participants.FirstOrDefault(p => p.UserId == request.TargetUserId)
            ?? throw new KeyNotFoundException($"User '{request.TargetUserId}' is not a participant");

        target.Role = request.Role;
        await conversationRepository.UpdateAsync(conversation, cancellationToken);
    }
}
