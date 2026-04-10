using Dumble.ChatService.Contracts.Conversations;
using MediatR;

namespace Dumble.ChatService.Application.Features.Conversations.Commands.CreateConversation;

public sealed record CreateConversationCommand(
    string CreatorId,
    string CreatorName,
    string? CreatorProfileImage,
    string Type,
    string? Name,
    List<string> ParticipantIds
) : IRequest<ConversationResponse>;
