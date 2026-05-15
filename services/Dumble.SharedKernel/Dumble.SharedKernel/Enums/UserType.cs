namespace Dumble.SharedKernel.Enums;

/// <summary>
/// Account roles. Mirrors the Auth service's enum names so JWT userType claims
/// parse cleanly. New users always start as <see cref="Participant"/>; any other
/// role requires admin approval (cert verification for Trainer, business-license
/// verification for GymOwner, etc.).
///
/// <para><see cref="GymOwner"/> is the human person who owns one or more gyms.</para>
/// <para><see cref="Gym"/> is the gym page/account itself, separate from its owner.</para>
/// </summary>
public enum UserType
{
    Participant,
    Moderator,
    Trainer,
    GymOwner,
    Gym,
    Admin
}
