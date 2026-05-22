using Dumble.BundleManagementService.Application.Contracts;
using Dumble.BundleManagementService.Application.Contracts.Repositories;
using Dumble.BundleManagementService.Application.Identity;
using Dumble.BundleManagementService.Domain.BundleAggregate;
using Dumble.BundleManagementService.Domain.BundleAggregate.Enums;
using Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects;
using Dumble.BundleManagementService.Domain.CategoryAggregate.ValueObjects;
using Dumble.SharedKernel.Contracts;
using Dumble.SharedKernel.Enums;
using MediatR;
using Name = Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects.Name;

namespace Dumble.BundleManagementService.Application.Features.Bundles.Commands.CreateBundle;

internal sealed class CreateBundleCommandHandler(
    ILoggedInUserService loggedInUserService,
    IFileService fileService,
    IGenericRepository<Bundle, BundleId> bundlesRepository) : IRequestHandler<CreateBundleCommand, BundleId>
{
    public async Task<BundleId> Handle(CreateBundleCommand request, CancellationToken cancellationToken)
    {
        var currentUser = loggedInUserService.GetCurrentUser();

        if (currentUser.UserType is not (UserType.GymOwner or UserType.Gym or UserType.Trainer))
            throw new UnauthorizedAccessException("Only gym owners, gyms, and trainers can create bundles");

        var ownerType = AccountIdentity.ToOwnerType(currentUser.UserType, currentUser.Roles);
        var accountId = AccountId.Create(AccountIdentity.ToAccountGuid(currentUser.Id));

        IEnumerable<BundleImage>? imageUrls = null;
        if (request.Images is not null)
        {
            var uploadTasks = request.Images.Select(async img =>
            {
                await using var stream = img.Content;
                return BundleImage.Create(await fileService.UploadAsync(stream, img.FileName, img.ContentType));
            });

            imageUrls = await Task.WhenAll(uploadTasks);
        }

        var bundle = Bundle.Create(
            accountId,
            ownerType,
            Name.Create(request.Name),
            Description.Create(request.Description),
            Price.Create(request.Price),
            Enum.Parse<Status>(request.Status, ignoreCase: true),
            CategoryId.Create(request.CategoryId),
            accountId,
            request.ExpiresOn,
            imageUrls
        );

        await bundlesRepository.Create(bundle);
        await bundlesRepository.CompleteAsync();

        return bundle.Id;
    }
}
