namespace Dumble.PostService.Domain.Enums;

/// <summary>
/// Who authored a post. Mirrors the auth UserType naming so the JWT
/// userType claim parses cleanly. <see cref="GymOwner"/> is the human owner;
/// <see cref="Gym"/> is the gym page itself posting on its own behalf.
/// </summary>
public enum AuthorType
{
    Participant,
    Trainer,
    GymOwner,
    Gym,
    Moderator
}
