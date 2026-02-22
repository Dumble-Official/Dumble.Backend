using Dumble.BundleManagementService.Application.Contracts.Repositories;
using Dumble.BundleManagementService.Domain.CategoryAggregate;
using Dumble.BundleManagementService.Domain.CategoryAggregate.ValueObjects;
using MediatR;
using Microsoft.Extensions.Logging;

namespace Dumble.BundleManagementService.Application.Features.Categories.Commands.DeleteCategoryCommand;

public sealed class DeleteCategoryCommandHandler(
    IGenericRepository<Category, CategoryId> categoryRepository
    ) : IRequestHandler<DeleteCategoryCommand>
{
    public async Task Handle(DeleteCategoryCommand request, CancellationToken cancellationToken)
    {
        // 1. Get Category From the database
        var categoryId = CategoryId.Create(request.Id);
        
        var category = await categoryRepository.Get(categoryId);

        // 2. Check if it exists or not 
        if (category is null) throw new Exception("NotFound");
        
        // 3. Delete it from the database
        categoryRepository.Delete(category);

        await categoryRepository.CompleteAsync();
    }
}