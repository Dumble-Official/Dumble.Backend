using Dumble.BundleManagementService.Application.Contracts;
using Dumble.BundleManagementService.Application.Contracts.Repositories;
using Dumble.BundleManagementService.Application.Identity;
using Dumble.BundleManagementService.Domain.BundleAggregate;
using Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects;
using Dumble.SharedKernel.Contracts;
using Dumble.SharedKernel.Enums;
using MediatR;

namespace Dumble.BundleManagementService.Application.Features.Bundles.Commands.DeleteBundle;

internal sealed class DeleteBundleCommandHandler(
    IFileService fileService,
    IGenericRepository<Bundle, BundleId> bundlesRepository,
    ILoggedInUserService loggedInUserService) : IRequestHandler<DeleteBundleCommand>
{
    public async Task Handle(DeleteBundleCommand request, CancellationToken cancellationToken)
    {
        var loggedInUser = loggedInUserService.GetCurrentUser();

        var bundle = await bundlesRepository.Get(BundleId.Create(request.Id))
            ?? throw new KeyNotFoundException($"Bundle {request.Id} not found");

        var currentUserAccountId = AccountId.Create(AccountIdentity.ToAccountGuid(loggedInUser.Id));

        // ADMIN can delete any bundle; other roles can only delete their own.
        if (!loggedInUser.IsInRole(UserType.Admin) && !currentUserAccountId.Equals(bundle.OwnerId))
            throw new UnauthorizedAccessException("You can only delete your own bundles");

        var tasks = bundle.Images.Select(img => fileService.DeleteAsync(img.Value));
        await Task.WhenAll(tasks);

        bundlesRepository.Delete(bundle);
        await bundlesRepository.CompleteAsync();
    }
}
